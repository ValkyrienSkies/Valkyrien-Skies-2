import torch
import time

x = torch.empty((7 * 1024**3 // 4,), device='cuda', dtype=torch.float32)

print("Allocated VRAM")
time.sleep(99999)
