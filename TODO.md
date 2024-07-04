- run influxdb as a docker container 

### Docker
dependencies
- /dev/ttyUSB* for HAN-port
- /dev/ttyAC* for Lora
- influxdb (via its own docker container)
- mqtt client
  - z-wave - stairs heater
  - victron
  - BMS java app on raspberry PI
- net/https https://hvakosterstroemmen.no
- listen on 9091/tcp - EV charging stations
- listen on 9090/tcp - web app + websocket
- /dev/USB for loRa RF
- video browser - access /mnt/motion
- firewall client - connect to 127.0.0.1:1000

