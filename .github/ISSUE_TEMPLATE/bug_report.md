---
name: Bug report
about: Let us know about a bug that occurs without other mods
title: ''
labels: bug
assignees: ''
body:
- type: checkboxes
  attributes:
    label: This issue occurs when only Valkyrien Skies is installed and no other mods
    options:
      - label: I have tested this issue and it occurs when no other mods are installed
- type: dropdown
  attributes:
    label: Minecraft Version
    description: What Minecraft version does this issue occur on?
    options:
      - 1.16
      - 1.18
      - 1.19
  validations:
    required: true
- type: dropdown
  attributes:
    label: Mod Loader
    description: What mod loader does this issue occur on?
    options:
      - Forge
      - Fabric
      - Quilt
  validations:
    required: true
---

**Describe the issue**
A clear and concise description of what the issue is

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'

**Logs**
Go to `.minecraft/logs` and upload the `latest.log` and `debug.log` file.
