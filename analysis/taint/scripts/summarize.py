"""Builds per-service taint summaries from Tai-e TaintFlow lines and composes them
across services through the channel-extractor model.

A summary maps (entry, input) -> exits. An input is a parameter index ('1', '1/couponCode'), a
request-DTO field ('OrderItemRequest.productId') or an event field ('OrderCancelledEvent.reason').
An exit is a local finding (CWE) or an outgoing channel (HTTP, AMQP, or the service's own database,
'db:<service>:<Entity>', whose entry is every read of the stored field); for AMQP the exit names the payload field
that carries the taint, so composition follows a message only into the listener input of that
same field (field-level). Exits marked 'coarse' say only that the payload object is tainted.

usage:
  summarize.py summary <service> <tai-e log> <model.json> <out.json> <app classes> <common classes>
  summarize.py compose [--coarse] <model.json> <summary.json>...
"""
import json
import os
import re
import subprocess
import sys

# TaintFlow{source -> sink call}; StoreFlow{source -> <Entity: T field>} (vkr.plugin.SpringDataModel)
ANY_FLOW = re.compile(r'^(TaintFlow|StoreFlow)\{(.*?) -> (<.*)\}$')
# param source:  <Handler: R m(..)>/1
PARAM_SRC = re.compile(r'^<([^:]+): \S+ ([^(]+)\([^)]*\)>/(\S+)$')
# field source:  <accessor()> [i@Ln] $r = %this.<Dto: T field>
FIELD_SRC = re.compile(r'^<[^>]*> \[[^\]]*\] \S+ = \S+\.<([^:]+): \S+ (\w+)>$')
SINK_CALL = re.compile(r'^<([^:]+): \S+ ([^(]+)\([^)]*\)>\[\d+@L(-?\d+)\] (.*)/(\S+)$')
STORE_FIELD = re.compile(r'^<([^:]+): \S+ (\w+)>$')
# Tai-e prints a constructor call as `$r5.<init>($r6, ...)`, without the class
CTOR_CALL = re.compile(r'invokespecial \S+\.<init>\(([^)]*)\)')
JAVAP = os.path.join(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-24'), 'bin', 'javap')
APP_PACKAGE = 'com.shop.'
EVENT_PACKAGE = 'com.shop.common.event.'
LOGGER_CALL = re.compile(r'\.(trace|debug|info|warn|error)\(')
SQL_CALL = re.compile(r'\.(createNativeQuery|createQuery|query\w*|update)\(')

_javap_cache = {}


def javap(classpath, cls):
    key = (classpath, cls)
    if key not in _javap_cache:
        _javap_cache[key] = subprocess.run([JAVAP, '-p', '-cp', classpath, cls],
                                           capture_output=True, text=True).stdout
    return _javap_cache[key]


def record_fields(classpath, cls):
    return [name for _, name in re.findall(r'private final (\S+) (\w+);', javap(classpath, cls))]


def roots(classpath, cls, seen=None):
    """Simple names of the classes in cls's package that (transitively) contain cls as a field."""
    seen = seen if seen is not None else set()
    if cls in seen:
        return set()
    seen.add(cls)
    pkg = cls.rsplit('.', 1)[0]
    simple = cls.rsplit('.', 1)[1]
    result = {simple}
    for f in os.listdir(os.path.join(classpath, *pkg.split('.'))):
        if f.endswith('.class') and f != simple + '.class' and '$' not in f:
            owner = pkg + '.' + f[:-6]
            if re.search(r'private final \S*?' + re.escape(cls) + r'[>;]', javap(classpath, owner)):
                result |= roots(classpath, owner, seen)
    return result


def by_handler(items, key):
    index = {}
    for item in items:
        h = item[key]
        index.setdefault((h['className'], h['method']), []).append(item)
    return index


def add_exits(entries, entry_ids, src_in, exits):
    for entry_id in entry_ids:
        e = entries.setdefault((entry_id, src_in), {'entry': entry_id, 'input': src_in, 'exits': []})
        for x in exits:
            if x not in e['exits']:
                e['exits'].append(x)


def summary(service, log_path, model_path, out_path, app_dir, common_dir):
    model = json.load(open(model_path, encoding='utf-8'))
    endpoints = [e for e in model['endpoints'] if e['service'] == service]
    listeners = [e for e in model['listeners'] if e['service'] == service]
    publishers = [p for p in model['publishers'] if p['service'] == service]
    handler_index = by_handler(endpoints + listeners, 'handler')
    clients = {}
    for c in model['httpClients']:
        if c['service'] == service:
            for site in c['callSites']:
                clients[(site['className'], site['method'], c['declaration']['method'])] = c

    def field_entries(cls):
        """Field sources are not tied to one handler: map the DTO / event to the entries taking it."""
        if '.entity.' in cls:
            return [f'db:{service}:{cls.rsplit(".", 1)[1]}']  # read back from the database
        if cls.startswith(EVENT_PACKAGE):
            names = roots(common_dir, cls)
            return [l['id'] for l in listeners if l['payloadType'] in names]
        names = roots(app_dir, cls)
        return [e['id'] for e in endpoints if any(p['type'] in names for p in e['params'])]

    event_classes = None

    def event_class_at(site_cls, site_method, argc):
        """The event record built by a constructor call with argc arguments in site_cls#site_method:
        a record of that arity that is (or is nested in) the payload of a publisher of site_cls."""
        nonlocal event_classes
        if event_classes is None:
            pkg = os.path.join(common_dir, *EVENT_PACKAGE.rstrip('.').split('.'))
            event_classes = [EVENT_PACKAGE + f[:-6] for f in os.listdir(pkg)
                             if f.endswith('.class') and '$' not in f]
        local = [p for p in publishers if p['origin']['className'] == site_cls]
        same = [p for p in local if p['origin']['method'] == site_method]
        for pubs in (same, local):
            hits = [c for c in event_classes if len(record_fields(common_dir, c)) == argc
                    and roots(common_dir, c) & {p['payloadType'] for p in pubs}]
            if hits:
                return hits
        return []

    def event_publishers(event_cls, site_cls, site_method, line):
        """Publishers whose payload is (or contains) the event built at this constructor call."""
        names = roots(common_dir, event_cls)
        cands = [p for p in publishers if p['payloadType'] in names
                 and p['origin']['className'] == site_cls]
        same_method = [p for p in cands if p['origin']['method'] == site_method]
        if same_method:
            # the enqueue right after the constructor call
            after = [p for p in same_method if p['origin']['line'] >= line] or same_method
            return [min(after, key=lambda p: p['origin']['line'] - line)]
        return cands  # payload built in a helper/lambda: every publisher of that type in the class

    entries, dropped = {}, 0
    for raw in open(log_path, encoding='utf-8', errors='replace'):
        raw = raw.strip()
        flow = ANY_FLOW.match(raw)
        if not flow:
            continue
        kind, source, sink = flow.groups()
        if m := PARAM_SRC.match(source):
            src_cls, src_m, src_in = m.groups()
            entry_ids = [h['id'] for h in handler_index.get((src_cls, src_m), [])] or [f'{src_cls}#{src_m}']
        elif m := FIELD_SRC.match(source):
            dto_cls, field = m.groups()
            src_in = f'{dto_cls.rsplit(".", 1)[1]}.{field}'
            entry_ids = field_entries(dto_cls) or [f'field:{src_in}']
        else:
            continue
        if kind == 'StoreFlow':
            entity, field = STORE_FIELD.match(sink).groups()
            entity = entity.rsplit('.', 1)[1]
            exits = [{'kind': 'CHANNEL', 'channel': f'db:{service}:{entity}', 'field': f'{entity}.{field}'}]
            add_exits(entries, entry_ids, src_in, exits)
            continue
        m = SINK_CALL.match(sink)
        if not m:
            continue
        sink_cls, sink_m, sink_line, call, sink_idx = m.groups()
        sink_line = int(sink_line)
        if not sink_cls.startswith(APP_PACKAGE):
            dropped += 1  # sink call site inside a library: not part of the service contract
            continue
        site = f'{sink_cls.rsplit(".", 1)[1]}#{sink_m}:{sink_line}'
        ctor = CTOR_CALL.search(call)
        callee = re.search(r'\.(\w+)\(', call).group(1) if not ctor else '<init>'
        if ctor:
            argc = len([a for a in ctor.group(1).split(',') if a.strip()])
            exits = []
            for event_cls in event_class_at(sink_cls, sink_m, argc):
                field = record_fields(common_dir, event_cls)[int(sink_idx)]
                exits += [{'kind': 'CHANNEL', 'channel': p['id'], 'field': f'{event_cls.rsplit(".", 1)[1]}.{field}'}
                          for p in event_publishers(event_cls, sink_cls, sink_m, sink_line)]
        elif (sink_cls, sink_m, callee) in clients:
            exits = [{'kind': 'CHANNEL', 'channel': clients[(sink_cls, sink_m, callee)]['id'], 'arg': sink_idx}]
        elif callee == 'enqueue':
            pubs = [p for p in publishers if p['origin']['className'] == sink_cls
                    and p['origin']['method'] == sink_m]
            exits = [{'kind': 'CHANNEL', 'channel': min(pubs, key=lambda p: abs(p['origin']['line'] - sink_line))['id'],
                      'coarse': True}] if pubs else []
        elif LOGGER_CALL.search(call):
            exits = [{'kind': 'LOCAL', 'cwe': 'CWE-117', 'site': site}]
        elif SQL_CALL.search(call):
            exits = [{'kind': 'LOCAL', 'cwe': 'CWE-89', 'site': site}]
        else:
            exits = [{'kind': 'UNKNOWN', 'site': site, 'call': call}]
        add_exits(entries, entry_ids, src_in, exits)
    result = {'service': service,
              'entries': sorted(entries.values(), key=lambda e: (e['entry'], e['input'])),
              'droppedLibrarySinks': dropped}
    json.dump(result, open(out_path, 'w', encoding='utf-8'), indent=2, ensure_ascii=False)
    return result


def compose(model_path, summary_paths, coarse=False):
    """Prints every chain from an entry input across channels to a local finding.

    Field-level (default): an AMQP exit carrying field F continues only into the receiving
    listener's input F. Coarse: any tainted payload continues into every input of the listener.
    """
    model = json.load(open(model_path, encoding='utf-8'))
    targets = {}
    for ch in model['channels']:
        targets.setdefault(ch['from'], []).append(ch)
    by_entry, summarized = {}, set()
    for p in summary_paths:
        s = json.load(open(p, encoding='utf-8'))
        summarized.add(s['service'])
        for e in s['entries']:
            by_entry.setdefault(e['entry'], []).append(e)

    def walk(e, path, seen):
        for x in e['exits']:
            if x['kind'] == 'LOCAL':
                yield path + [f"{x['cwe']} @ {x['site']}"]
                continue
            if x['kind'] != 'CHANNEL' or (x.get('coarse') and not coarse) or ('field' in x and coarse):
                continue
            chans = targets.get(x['channel'], [])
            if x['channel'].startswith('db:'):  # stored by one request, read by another of the service
                chans = [{'kind': 'DB', 'to': x['channel'], 'attributes': {'routingKey': x['channel'].split(':')[2]}}]
            for ch in chans:
                hop = f"--{ch['kind']} {ch['attributes'].get('routingKey') or ch['attributes'].get('endpointPath')}"
                hop += f" [{x['field']}]-->" if 'field' in x else '-->'
                inputs = by_entry.get(ch['to'], [])
                if 'field' in x:
                    inputs = [i for i in inputs if i['input'] == x['field']]
                if ch['to'].split(':')[1] not in summarized:
                    yield path + [hop, ch['to'] + '  (no summary yet)']
                    continue
                if not inputs:
                    yield path + [hop, ch['to'] + '  (no sink for this input)']
                for i in inputs:
                    if (i['entry'], i['input']) not in seen:
                        yield from walk(i, path + [hop, f"{i['entry']} <{i['input']}>"],
                                        seen | {(i['entry'], i['input'])})

    for entry_id in sorted(by_entry):
        if entry_id.startswith('db:'):
            continue  # stored data is tainted only through a chain from an entry point
        for e in by_entry[entry_id]:
            # chains without a channel hop are findings inside one service
            for p in walk(e, [f"{entry_id} <{e['input']}>"], {(entry_id, e['input'])}):
                print('  '.join(p))


if __name__ == '__main__':
    if sys.argv[1] == 'summary':
        r = summary(*sys.argv[2:8])
        for e in r['entries']:
            print(e['entry'], f"<{e['input']}>")
            for x in e['exits']:
                print('   ', x)
        print('dropped library sinks:', r['droppedLibrarySinks'])
    else:
        coarse = '--coarse' in sys.argv
        args = [a for a in sys.argv[2:] if a != '--coarse']
        compose(args[0], args[1:], coarse)
