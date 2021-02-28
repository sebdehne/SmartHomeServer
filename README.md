# SmartHomeServer
Smarthome backend and frontend

### Features:
- Management of my EV Charging stations (See [hardware](https://github.com/sebdehne/EvChargingStationHardware) and [firmware](https://github.com/sebdehne/EvChargingStationFirmware)) 
  - Charge only when energy prices are low (overrideable)
  - Intelligent load sharing between multiple stations
  - Logging of all parameters, incl. power consumption
- Heating control
  - Heat only when energy prices are low
  - Keep certain temperature
- Garage door
  - Auto closing after a configurable timeout
- Temperature, humidity & light sensor logging

### Frontend:
- React frontend
- material UI styring

### Backend:
- pure Kotlin - no Spring
- Jetty / WebSockets
