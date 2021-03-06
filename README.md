# SmartHomeServer
Smarthome backend and frontend

### Features:
- Management of my DehneEVSE Charging stations (See [hardware](https://github.com/sebdehne/DehneEVSE-Hardware) and [firmware](https://github.com/sebdehne/DehneEVSE-Firmware)) 
  - Charge only when energy prices are low (overrideable)
  - Intelligent load sharing between multiple stations
  - Logging of all parameters, incl. power consumption to InfluxDB
- Heating control
  - Heat only when energy prices are low
  - Keep certain temperature
  - Logging of all parameters to InfluxDB
- Garage door
  - Auto closing after a configurable timeout
  - Logging of all parameters to InfluxDB
- Temperature, humidity & light sensor logging to InfluxDB
- Camera live viewer using WebRTC (via [RTSPtoWebRTC](https://github.com/deepch/RTSPtoWebRTC))

### Frontend:
- React frontend
- material UI styring

### Backend:
- pure Kotlin - no Spring
- Jetty / WebSockets
