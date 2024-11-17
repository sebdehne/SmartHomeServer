# ModBus recording between Hörmann SupraMatic E4 and UAP-1-HCP

## Test cases

### Triggered by UAP-1-HCP

- 01: bus-scan + wait until light off
- 02: closed: trigger light + wait + trigger light (off)
- 03: closed: close (light goes on)
- 04: closed: open -> opening -> opened -> !close -> closing -> closed (light stil on at the end)
- 05: closed: open -> open while opening (stops) -> open -> open (stops again) -> open -> opened -> close
- 06: closed: open -> stop (paused) -> stop (closes) -> stop (paused) -> stop (closes)
- 07: closed: halv open (opens a little, not halv but more than vent) -> halv open again -> closes
- 08: closed: vent -> (goes into vent-mode) -> vent (closes)

### triggered via bluetooth app
- 09: closed: open -> wait -> close
- 10: closed & light off: toggle light

### Idle
- 11: 10+ minutes of idle traffic
