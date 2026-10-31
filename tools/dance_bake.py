"""Turns tracked dances (dance_track.py) into the joint angles of the player model and writes DanceTracks.java.

python dance_bake.py <dir with Rat.npz, Rampage.npz, Funked_up.npz> <out DanceTracks.java>

Model axes match the tracker: x to the left of the dancer, y down, z to their back.
"""
import sys

import numpy as np

RATE = 25
LEG = 6.0
SPREAD = 1.22
# name, file, loop search start, loop search end, crossfade seconds, use jumps (the camera has to stand still)
DANCES = [
    ('RAT', 'Rat', (2.2, 2.9), (8.6, 9.5), 0.3, False),
    ('RAMPAGE', 'Rampage', (0.0, 1.2), (12.4, 14.8), 0.4, False),
    ('FUNK', 'Funked_up', (0.5, 1.0), None, 0.2, True),
]
FUNK_PERIOD = 8.83


def fill(a):
    a = a.copy()
    t = np.arange(len(a))
    for j in range(33):
        for k in range(3):
            good = ~np.isnan(a[:, j, k])
            a[:, j, k] = np.interp(t, t[good], a[good, j, k])
    return a


def smooth(a, sigma):
    r = int(sigma * 3)
    w = np.exp(-0.5 * (np.arange(-r, r + 1) / sigma) ** 2)
    w /= w.sum()
    out = np.empty_like(a)
    flat = a.reshape(len(a), -1)
    res = np.stack([np.convolve(np.pad(flat[:, i], r, mode='edge'), w, mode='valid') for i in range(flat.shape[1])], axis=1)
    return res.reshape(a.shape)


def unit(v):
    return v / (np.linalg.norm(v) + 1e-9)


def euler(r):
    y = np.arcsin(np.clip(-r[2, 0], -1, 1))
    return np.arctan2(r[2, 1], r[2, 2]), y, np.arctan2(r[1, 0], r[0, 0])


def steady(r, last):
    """The same rotation has two sets of angles: the one nearest to the last frame keeps a limb from flipping."""
    a = np.array(euler(r))
    b = np.array([a[0] + np.pi, np.pi - a[1], a[2] + np.pi])
    if last is None:
        return a
    a += 2 * np.pi * np.round((last - a) / (2 * np.pi))
    b += 2 * np.pi * np.round((last - b) / (2 * np.pi))
    return a if np.abs(a - last).sum() <= np.abs(b - last).sum() else b


def turn(y, z):
    cy, sy, cz, sz = np.cos(y), np.sin(y), np.cos(z), np.sin(z)
    return np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])


def limb(parent, a, b, c, sign, prev):
    """Rotation of a limb in its parent frame; sign -1 bends forwards (arms), +1 backwards (knees)."""
    u = unit(parent.T @ (b - a))
    f = unit(parent.T @ (c - b))
    bend = float(np.arccos(np.clip(u @ f, -1, 1)))
    side = f - u * (u @ f)
    z = prev - u * (u @ prev)
    if np.linalg.norm(side) > 1e-4:
        # nearly straight limbs do not say which way the joint points: keep the last direction
        k = min(1.0, bend / 0.35)
        z = (1 - k) * unit(z) + k * unit(side) * sign
    z = unit(z - u * (u @ z))
    x = unit(np.cross(u, z))
    r = np.stack([x, u, np.cross(x, u)], axis=1)
    return r, sign * bend, r[:, 2].copy()


def frame(x, y):
    x = unit(x)
    y = unit(y - x * (x @ y))
    return np.stack([x, y, np.cross(x, y)], axis=1)


def bake(world, image, fps, jumps):
    n = len(world)
    # gravity: the camera may look up at the dancer, the average spine is close to vertical
    spine = np.mean((world[:, 23] + world[:, 24]) - (world[:, 11] + world[:, 12]), axis=0)
    s = unit(spine)
    down = np.array([0, 1.0, 0])
    v = np.cross(s, down)
    c = s @ down
    k = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    tilt = np.eye(3) + k + k @ k / (1 + c)
    world = world @ tilt.T

    # the tracker turns the hips, the shoulders and the head by the same amount in every dance: a dancer faces the
    # camera on average, so the median of each is its error and not a movement
    H0 = [frame(p[23] - p[24], np.array([0, 1.0, 0])) for p in world]
    yaw = float(np.median([euler(h)[1] for h in H0]))
    world = world @ turn(-yaw, 0).T

    def chest(p):
        return frame(p[11] - p[12], (p[23] + p[24]) / 2 - (p[11] + p[12]) / 2)

    def face(p):
        x = unit(p[7] - p[8])
        back = (p[7] + p[8]) / 2 - p[0]
        z = unit(back - x * (x @ back))
        return np.stack([x, np.cross(z, x), z], axis=1)

    bends = np.array([euler(frame(p[23] - p[24], np.array([0, 1.0, 0])).T @ chest(p)) for p in world])
    chest_fix = turn(np.median(bends[:, 1]), np.median(bends[:, 2])).T
    heads = np.array([euler((chest(p) @ chest_fix).T @ face(p)) for p in world])
    head_fix = turn(np.median(heads[:, 1]), np.median(heads[:, 2])).T

    hx = (image[:, 23, 0] + image[:, 24, 0]) / 2
    hy = (image[:, 23, 1] + image[:, 24, 1]) / 2
    torso = np.linalg.norm((image[:, 23, :2] + image[:, 24, :2]) / 2 - (image[:, 11, :2] + image[:, 12, :2]) / 2, axis=1)
    px = 12.0 / np.maximum(torso, 1e-3)
    window = int(fps * 2.5) | 1
    kernel = np.ones(window) / window
    slow = np.convolve(np.pad(hx, window // 2, mode='edge'), kernel, mode='valid')
    side = (hx - slow) * px

    out = np.zeros((n, 27))
    z = {'ra': np.array([0, 0, 1.0]), 'la': np.array([0, 0, 1.0]), 'rl': np.array([0, 0, 1.0]), 'll': np.array([0, 0, 1.0])}
    stands = np.zeros(n)
    last = {}

    def track(key, r):
        last[key] = steady(r, last.get(key))
        return last[key]

    for i in range(n):
        p = world[i]
        hips = (p[23] + p[24]) / 2
        shoulders = (p[11] + p[12]) / 2
        H = frame(p[23] - p[24], np.array([0, 1.0, 0]))
        C = chest(p) @ chest_fix
        W = H.T @ C
        Hd = C.T @ face(p) @ head_fix
        ra, re, z['ra'] = limb(C, p[12], p[14], p[16], -1, z['ra'])
        la, le, z['la'] = limb(C, p[11], p[13], p[15], -1, z['la'])
        rl, rk, z['rl'] = limb(H, p[24], p[26], p[28], 1, z['rl'])
        ll, lk, z['ll'] = limb(H, p[23], p[25], p[27], 1, z['ll'])
        hr = track('hip', H)
        wr = track('waist', W)
        dr = track('head', Hd)
        foot = []
        for r, b in ((rl, rk), (ll, lk)):
            knee = r @ np.array([0, LEG, 0])
            ankle = r @ np.array([0, LEG * np.cos(b), LEG * np.sin(b)])
            foot.append((H @ (knee + ankle))[1])
        stands[i] = max(foot)
        out[i] = [side[i], 0, *hr, *wr, *dr, *track('ra', ra), re, *track('la', la), le, *track('rl', rl), rk, *track('ll', ll), lk]
    # keep the hips near their resting angles and the lower foot on the floor
    lift = np.zeros(n)
    if jumps:
        rise = (np.median(hy) - hy) * px
        lift = np.clip((rise - np.median(rise)) - (stands - np.median(stands)), 0, 6)
        lift[lift < 1.0] = 0
    # a blocky model reads narrower than a person: open the stance a little, the hips drop so the feet stay down
    low = np.minimum(np.abs(out[:, 21]), np.abs(out[:, 25]))
    out[:, 21] *= SPREAD
    out[:, 25] *= SPREAD
    out[:, 1] = 2 * LEG - stands - lift + 2 * LEG * (np.cos(low) - np.cos(low * SPREAD))
    out[:, 0] = np.clip(out[:, 0], -5, 5)
    return out


def pick(vec, fps, a, b):
    """The start and end of a segment whose two ends look alike, so the loop closes softly."""
    best = (1e9, 0, 0)
    for i in range(int(a[0] * fps), int(a[1] * fps)):
        for j in range(int(b[0] * fps), min(int(b[1] * fps), len(vec))):
            e = np.mean(np.abs(vec[i] - vec[j]))
            if e < best[0]:
                best = (e, i, j)
    return best


def main():
    root, target = sys.argv[1:3]
    lines = []
    for name, file, a, b, fade, jumps in DANCES:
        d = np.load(root + '/' + file + '.npz')
        fps = float(d['fps'])
        world = smooth(fill(d['world']), 1.0)
        image = smooth(fill(d['image']), 1.0)
        tracks = bake(world, image, fps, jumps)
        if b is None:
            b = (a[0] + FUNK_PERIOD - 0.15, a[1] + FUNK_PERIOD + 0.15)
        err, i, j = pick(tracks[:, 2:], fps, a, b)
        print(name, 'loop %.2f..%.2f s' % (i / fps, j / fps), 'mismatch %.3f' % err)
        count = int((j - i) / fps * RATE)
        t = i + np.arange(count) * fps / RATE
        table = np.stack([np.interp(t, np.arange(len(tracks)), tracks[:, k]) for k in range(27)], axis=1)
        m = int(fade * RATE)
        for q in range(m):
            w = (q + 1) / (m + 1)
            w = w * w * (3 - 2 * w)
            table[count - m + q] = table[count - m + q] * (1 - w) + table[0] * w
        rows = ';'.join(','.join(str(int(round(v * 100))) for v in row) for row in table)
        lines.append('\tstatic final DanceTrack %s = new DanceTrack(%d, "%s");\n' % (name, RATE, rows))
        print(name, 'chars', len(rows), 'seconds', count / RATE, 'source start', i / fps)
    text = ('package com.zerovisual.emote;\n\n'
            '/** Dances tracked from reference videos. Generated by tools/dance_bake.py, do not edit by hand. */\n'
            'final class DanceTracks {\n' + ''.join(lines) + '\n\tprivate DanceTracks() {\n\t}\n}\n')
    with open(target, 'w', encoding='utf-8', newline='') as f:
        f.write(text)


main()
