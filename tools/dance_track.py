"""Tracks a dancer in a reference video and saves the pose of every frame.

python dance_track.py <model.task> <video> <out.npz>

Saves world landmarks (metres around the hips, x to the dancer's left, y down, z towards the back of the dancer),
image landmarks and their visibility. dance_bake.py turns them into the tables of DanceTracks.java.
"""
import sys

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python as tasks
from mediapipe.tasks.python import vision


def main():
    model, video, out = sys.argv[1:4]
    options = vision.PoseLandmarkerOptions(
        base_options=tasks.BaseOptions(model_asset_path=model),
        running_mode=vision.RunningMode.VIDEO,
        min_pose_detection_confidence=0.3,
        min_tracking_confidence=0.3,
        num_poses=1,
    )
    capture = cv2.VideoCapture(video)
    fps = capture.get(cv2.CAP_PROP_FPS)
    world, image, seen = [], [], []
    with vision.PoseLandmarker.create_from_options(options) as tracker:
        n = 0
        while True:
            ok, frame = capture.read()
            if not ok:
                break
            picture = mp.Image(image_format=mp.ImageFormat.SRGB, data=cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
            result = tracker.detect_for_video(picture, int(n * 1000 / fps))
            if result.pose_world_landmarks:
                world.append([[p.x, p.y, p.z] for p in result.pose_world_landmarks[0]])
                image.append([[p.x, p.y, p.z] for p in result.pose_landmarks[0]])
                seen.append([p.visibility for p in result.pose_landmarks[0]])
            else:
                world.append(np.full((33, 3), np.nan).tolist())
                image.append(np.full((33, 3), np.nan).tolist())
                seen.append([0.0] * 33)
            n += 1
    np.savez(out, world=np.array(world), image=np.array(image), seen=np.array(seen), fps=fps)
    found = int(np.sum(~np.isnan(np.array(world)[:, 0, 0])))
    print(out, 'frames', n, 'tracked', found, 'fps', fps)


main()
