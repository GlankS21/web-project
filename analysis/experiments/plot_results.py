"""Draws the figures of the experiment chapter from the outputs of run_experiments.py and precision.py.

fig-time.png      incremental (warm cache) vs full analysis (empty cache) per scenario, RQ1
fig-precision.png precision of chains and findings after each false-positive reduction step

usage: plot_results.py [--warm results-final-warm.csv] [--cold results-final.csv] [--out DIR]
input: <VKR_EXP>/out/{<warm>,<cold>,labels-before-*.csv,labels.csv}
output: <DIR>/fig-time.png, <DIR>/fig-precision.png (default <VKR_EXP>/out/figures)
"""
import argparse
import csv
import os

import matplotlib

matplotlib.use('Agg')
import matplotlib.pyplot as plt

from precision import OUT, finding_key

WARM, COLD = '#2a78d6', '#eb6834'
INK, MUTED, GRID = '#0b0b0b', '#52514e', '#e4e3df'

# label snapshots kept before each step; the label is the step's result
STEPS = [('labels-before-barriers.csv', 'Исходно'),
         ('labels-before-nontext.csv', '@Pattern,\nисключения'),
         ('labels-before-listener.csv', 'нетекстовые\nтипы'),
         ('labels-before-fresh.csv', 'корни-\nслушатели'),
         ('labels-before-payment.csv', 'новые\nсущности'),
         ('labels.csv', '+payment-\nservice')]


def rows(name):
    return list(csv.DictReader(open(os.path.join(OUT, name), encoding='utf-8')))


def style(ax):
    for side in ('top', 'right'):
        ax.spines[side].set_visible(False)
    for side in ('left', 'bottom'):
        ax.spines[side].set_color(GRID)
    ax.tick_params(colors=MUTED, length=0)
    ax.set_axisbelow(True)


def plot_time(warm, cold, path):
    w = {r['scenario']: r for r in rows(warm)}
    c = {r['scenario']: r for r in rows(cold)}
    ids = [s for s in w if s != 'S00' and s in c]  # S00 fills the cache, it has no incremental run
    fig, ax = plt.subplots(figsize=(7, 7.5))
    style(ax)
    ax.grid(axis='x', color=GRID, linewidth=0.8)
    h = 0.38
    for i, s in enumerate(ids):
        ws, cs = float(w[s]['seconds']), float(c[s]['coldSeconds'])
        y = len(ids) - 1 - i
        ax.barh(y + h / 2 + 0.01, ws, h, color=WARM, edgecolor='white', linewidth=1,
                label='инкрементальный' if i == 0 else None)
        ax.barh(y - h / 2 - 0.01, cs, h, color=COLD, edgecolor='white', linewidth=1,
                label='полный' if i == 0 else None)
        n = len(w[s]['rerun'].split())
        ax.text(max(ws, cs) + 15, y, f'×{cs / ws:.1f}' if ws >= 10 else f'×{cs / ws:.0f}', va='center', color=INK,
                fontsize=8)
        ax.text(-20, y, f'{s} ({n})', va='center', ha='right', color=MUTED, fontsize=8)
    ax.set_yticks([])
    ax.set_xlim(0, max(max(float(c[s]['coldSeconds']), float(w[s]['seconds'])) for s in ids) * 1.12)
    ax.set_xlabel('время анализа, с', color=MUTED)
    ax.legend(loc='upper center', bbox_to_anchor=(0.5, 1.06), ncol=2, frameon=False, labelcolor=INK)
    fig.text(0.01, 0.005, 'в скобках: число перезапущенных сервисов; справа: ускорение', color=MUTED, fontsize=7)
    fig.tight_layout()
    fig.savefig(path, dpi=200)
    plt.close(fig)


def precision_of(labels):
    chains = sum(r['label'] == 'TP' for r in labels) / len(labels)
    findings = {}
    for r in labels:
        k = finding_key(r['chain'])
        findings[k] = findings.get(k, False) or r['label'] == 'TP'
    return chains, sum(findings.values()) / len(findings), len(labels), len(findings)


def plot_precision(path):
    data = [precision_of(rows(name)) for name, _ in STEPS]
    xs = range(len(STEPS))
    fig, ax = plt.subplots(figsize=(7, 3.8))
    style(ax)
    ax.grid(axis='y', color=GRID, linewidth=0.8)
    for idx, color, name in ((0, WARM, 'цепочки'), (1, COLD, 'находки')):
        ys = [d[idx] for d in data]
        ax.plot(xs, ys, color=color, linewidth=2, marker='o', markersize=6,
                markeredgecolor='white', markeredgewidth=1.5, label=name)
        ax.text(xs[-1] + 0.12, ys[-1], f'{ys[-1]:.2f}', va='center', color=INK, fontsize=8)
        ax.text(xs[0], ys[0] + (0.03 if idx == 0 else -0.04), f'{ys[0]:.2f}', ha='center', color=INK, fontsize=8)
    ax.set_xticks(list(xs), [f'{s}\n{d[2]} / {d[3]}' for (_, s), d in zip(STEPS, data)], fontsize=8, color=MUTED)
    ax.set_ylim(0, 0.7)
    ax.set_xlim(-0.6, len(STEPS) - 0.4)
    ax.set_ylabel('точность (precision)', color=MUTED)
    ax.legend(loc='upper left', frameon=False, labelcolor=INK)
    fig.text(0.01, 0.005, 'под осью: число цепочек / находок', color=MUTED, fontsize=7)
    fig.tight_layout()
    fig.savefig(path, dpi=200)
    plt.close(fig)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--warm', default='results-final-warm.csv')
    ap.add_argument('--cold', default='results-final.csv')
    ap.add_argument('--out', default=os.path.join(OUT, 'figures'))
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    plot_time(a.warm, a.cold, os.path.join(a.out, 'fig-time.png'))
    plot_precision(os.path.join(a.out, 'fig-precision.png'))
    print(a.out)


if __name__ == '__main__':
    main()
