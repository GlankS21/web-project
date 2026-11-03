"""Builds the database-channel config of a service from its field-level one: adds the entity
field sources (mk_entity_fields.py) and summarises static String helpers at their call sites.

A helper such as Order.truncate(reason) has one call site under 1-call context sensitivity, so
every string passed through it is merged into one return value and flows back to all callers.
The helper's parameter is sanitized (taint does not enter its body) and a transfer from the
argument to the result is applied at each call site, in the caller's context instead.
usage: mk_db_config.py <field.yml> <entity-sources.yml> <out.yml>"""
import sys

HELPERS = [
    '<com.shop.common.util.TextUtils: java.lang.String truncate(java.lang.String,int)>',
    '<com.shop.common.util.CorrelationIdUtils: java.lang.String resolveOrGenerate(java.lang.String)>',
    '<com.shop.order.entity.Order: java.lang.String truncate(java.lang.String)>',
    '<com.shop.order.entity.Coupon: java.lang.String normalizeCode(java.lang.String)>',
    '<com.shop.order.messaging.outbox.OutboxPublisher: java.lang.String truncate(java.lang.String)>',
    '<com.shop.order.repository.CouponSpecifications: java.lang.String escapeLike(java.lang.String)>',
    '<com.shop.order.repository.OrderSpecifications: java.lang.String escapeLike(java.lang.String)>',
    '<com.shop.inventory.messaging.outbox.OutboxPublisher: java.lang.String truncate(java.lang.String)>',
    '<com.shop.inventory.service.impl.StockReservationServiceImpl: java.lang.String truncate(java.lang.String)>',
    '<com.shop.payment.messaging.outbox.OutboxPublisher: java.lang.String truncate(java.lang.String)>',
    '<com.shop.payment.gateway.yookassa.YooKassaPaymentGateway: java.lang.String truncate(java.lang.String)>',
]

# JDK String methods that return their receiver (or argument) through an internal helper with a
# single call site, e.g. toUpperCase -> StringLatin1.toUpperCase(this, ...) returns `this` when
# nothing changes: under 1-call every String ever passed through them meets in that helper's
# return value. Taint is kept out of their bodies (sanitizer) and transferred at the call site.
JDK_STRING = [
    ('<java.lang.String: java.lang.String trim()>', 'base'),
    ('<java.lang.String: java.lang.String strip()>', 'base'),
    ('<java.lang.String: java.lang.String stripLeading()>', 'base'),
    ('<java.lang.String: java.lang.String stripTrailing()>', 'base'),
    ('<java.lang.String: java.lang.String toUpperCase()>', 'base'),
    ('<java.lang.String: java.lang.String toLowerCase()>', 'base'),
    ('<java.lang.String: java.lang.String toUpperCase(java.util.Locale)>', 'base'),
    ('<java.lang.String: java.lang.String toLowerCase(java.util.Locale)>', 'base'),
    ('<java.lang.String: java.lang.String substring(int)>', 'base'),
    ('<java.lang.String: java.lang.String substring(int,int)>', 'base'),
    ('<java.lang.String: java.lang.String replace(java.lang.CharSequence,java.lang.CharSequence)>', 'base'),
    ('<java.lang.String: java.lang.String intern()>', 'base'),
    ('<java.lang.String: java.lang.String valueOf(java.lang.Object)>', '0'),  # transfer already in config
    ('<java.lang.AbstractStringBuilder^: * append(java.lang.Object^)>', '0'),  # transfer already in config
]

# Stream pipelines: elements travel through Sink objects allocated inside the JDK, so under 1-call
# the elements of every stream in the service (and of maps built by Collectors.toMap) meet in the
# same Sink and reach every lambda of every pipeline. Taint is kept out of the pipeline (sanitizer
# on Sink.accept, which every stage and collector accumulator is fed through) and follows the
# stream object instead: Collection.stream / stage / collect transfers base -> result.
PIPELINE_SANITIZERS = [('<java.util.stream.Sink^: void accept(java.lang.Object)>', '0')]
STREAM_STAGES = ['sorted()', 'sorted(java.util.Comparator)', 'distinct()', 'limit(long)', 'skip(long)',
                 'flatMap(java.util.function.Function)', 'peek(java.util.function.Consumer)']
STREAM_TRANSFERS = [f'<java.util.stream.Stream^: java.util.stream.Stream {m}>' for m in STREAM_STAGES]

# Collection internals: HashMap.table/Node, ArrayList.elementData, ImmutableCollections arrays are
# allocated inside the JDK without heap context, so every collection of the service shares them
# and an element added anywhere comes out of every iterator (observed: HashSet.iterator().next()
# in OrderServiceImpl.singleCurrency held 248 taints). Taint is kept out of the internals; it is
# attached to the application's container object by vkr.plugin.ContainerTaint at the call site and
# read back through the iterator/next/get/getKey/getValue/stream transfers.
CONTAINER_SANITIZERS = [
    ('<java.util.Collection^: boolean add(java.lang.Object)>', '0'),
    ('<java.util.List^: void add(int,java.lang.Object)>', '1'),
    ('<java.util.List^: java.lang.Object set(int,java.lang.Object)>', '1'),
    ('<java.util.Deque^: void addFirst(java.lang.Object)>', '0'),
    ('<java.util.Deque^: void addLast(java.lang.Object)>', '0'),
    ('<java.util.Deque^: void push(java.lang.Object)>', '0'),
    ('<java.util.Queue^: boolean offer(java.lang.Object)>', '0'),
    ('<java.util.Map^: java.lang.Object put(java.lang.Object,java.lang.Object)>', '0'),
    ('<java.util.Map^: java.lang.Object put(java.lang.Object,java.lang.Object)>', '1'),
    ('<java.util.Map^: java.lang.Object putIfAbsent(java.lang.Object,java.lang.Object)>', '0'),
    ('<java.util.Map^: java.lang.Object putIfAbsent(java.lang.Object,java.lang.Object)>', '1'),
    ('<java.util.Map^: java.lang.Object merge(java.lang.Object,java.lang.Object,java.util.function.BiFunction)>', '0'),
    ('<java.util.Map^: java.lang.Object merge(java.lang.Object,java.lang.Object,java.util.function.BiFunction)>', '1'),
    ('<java.util.List: java.util.List of(java.lang.Object)>', '0'),  # transfer 0 -> result in config
]

# vkr.plugin.JdkTaintBarrier keeps taint out of every java.util.* method body; the ones an
# application value passes through need a call-site transfer instead
UTIL_TRANSFERS = [
    ('<java.util.Objects: java.lang.Object requireNonNull(java.lang.Object)>', '0'),
    ('<java.util.Objects: java.lang.Object requireNonNull(java.lang.Object,java.lang.String)>', '0'),
    ('<java.util.Objects: java.lang.Object requireNonNullElse(java.lang.Object,java.lang.Object)>', '0'),
    ('<java.util.Objects: java.lang.Object requireNonNullElse(java.lang.Object,java.lang.Object)>', '1'),
    ('<java.util.Objects: java.lang.String toString(java.lang.Object)>', '0'),
    ('<java.util.Optional: java.util.Optional of(java.lang.Object)>', '0'),
    ('<java.util.Optional: java.util.Optional ofNullable(java.lang.Object)>', '0'),
    ('<java.util.Optional: java.util.Optional map(java.util.function.Function)>', 'base'),
    ('<java.util.Optional: java.util.Optional filter(java.util.function.Predicate)>', 'base'),
    ('<java.util.Optional: java.lang.Object get()>', 'base'),
    ('<java.util.Optional: java.lang.Object orElseThrow()>', 'base'),
    ('<java.util.Optional: java.lang.Object orElseThrow(java.util.function.Supplier)>', 'base'),
    ('<java.util.Optional: java.lang.Object orElse(java.lang.Object)>', 'base'),
    ('<java.util.Optional: java.lang.Object orElse(java.lang.Object)>', '0'),
    ('<java.util.UUID: java.lang.String toString()>', 'base'),
    ('<java.util.UUID: java.util.UUID fromString(java.lang.String)>', '0'),
    ('<java.util.List: java.util.List copyOf(java.util.Collection)>', '0'),
    ('<java.util.Set: java.util.Set copyOf(java.util.Collection)>', '0'),
    ('<java.util.Collection^: boolean addAll(java.util.Collection)>', '0'),  # -> base
]

# java.math is behind the barrier too (BigDecimal returns `this` or cached constants from helpers
# shared by every amount of the service): arithmetic is summarised at the call site
BD = 'java.math.BigDecimal'
BIGDECIMAL_TRANSFERS = [
    (f'<{BD}: {BD} {m}({p})>', i)
    for m, p in [('add', BD), ('subtract', BD), ('multiply', BD), ('min', BD), ('max', BD),
                 ('divide', f'{BD},java.math.RoundingMode'), ('divide', f'{BD},int,java.math.RoundingMode'),
                 ('remainder', BD)]
    for i in ('base', '0')
] + [(f'<{BD}: {BD} {m}({p})>', 'base') for m, p in [
    ('setScale', 'int,java.math.RoundingMode'), ('setScale', 'int'), ('abs', ''), ('negate', ''),
    ('stripTrailingZeros', '')]] + [
    (f'<{BD}: {BD} valueOf(long)>', '0'), (f'<{BD}: {BD} valueOf(double)>', '0'),
    (f'<{BD}: java.lang.String toPlainString()>', 'base'), (f'<{BD}: java.lang.String toString()>', 'base'),
]

src, entities, out = sys.argv[1:4]
s = open(src, encoding='utf-8').read()
assert '\nsinks:\n' in s and '\ntransfers:\n' in s and 'sanitizers:' not in s
s = s.replace('\nsinks:\n', '\n' + open(entities, encoding='utf-8').read() + '\nsinks:\n', 1)
s = s.replace('\ntransfers:\n', '\ntransfers:\n  # static String helpers, summarised at the call site\n'
              + ''.join(f'  - {{ method: "{h}", from: 0, to: result }}\n' for h in HELPERS)
              + ''.join(f'  - {{ method: "{m}", from: base, to: result }}\n' for m, i in JDK_STRING
                        if i == 'base' and f'"{m}", from: base' not in s)
              + ''.join(f'  - {{ method: "{m}", from: base, to: result }}\n' for m in STREAM_TRANSFERS)
              + ''.join(f'  - {{ method: "{m}", from: {i}, to: {"base" if "addAll" in m else "result"} }}\n'
                        for m, i in UTIL_TRANSFERS if f'"{m}", from: {i}' not in s)
              + ''.join(f'  - {{ method: "{m}", from: {i}, to: result }}\n'
                        for m, i in BIGDECIMAL_TRANSFERS if f'"{m}", from: {i}' not in s), 1)
s += ('\nsanitizers:\n' + ''.join(f'  - {{ kind: param, method: "{h}", index: 0 }}\n' for h in HELPERS)
      + ''.join(f'  - {{ kind: param, method: "{m}", index: {i} }}\n' for m, i in JDK_STRING + PIPELINE_SANITIZERS + CONTAINER_SANITIZERS))
open(out, 'w', encoding='utf-8').write(s)
