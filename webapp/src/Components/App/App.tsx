import React from 'react';
import { Button, Container, Link } from "@material-ui/core";
import { useNavigate } from "react-router-dom";
import './App.css';

const App = () => {

    let navigate = useNavigate();

    const relative = (path: string) => () => {
        navigate(path);
    };

    return (
        <Container maxWidth="sm" className="App">
            <h2 style={{ textAlign: "center" }}>Smart home controller</h2>
            <ul className="Menu">
                <li>
                    <Button variant="contained" color="primary" onClick={relative("/garage")}>
                        Garage door
                    </Button>
                </li>
                <li>
                    <Button variant="contained" color="primary" onClick={relative("/heater")}>
                        Heater under floor
                    </Button>
                </li>
                <li>
                    <Button variant="contained" color="primary" onClick={relative("/evChargingStations")}>
                        EV Charging
                    </Button>
                </li>
                <li>
                    <Button variant="contained" color="primary" onClick={relative("/environmentSensors")}>
                        Environment Sensors
                    </Button>
                </li>
                <li>
                    <Button variant="contained" color="primary" onClick={relative("/webcams")}>
                        Cameras
                    </Button>
                </li>
                <li>
                    <Button variant="contained" color="primary" onClick={relative("/recordings")}>
                        Video recordings
                    </Button>
                </li>
                <li>
                    <Link href="https://dehnes.com/stats/d/000000007/current">
                        <Button variant="contained" color="primary">
                            Temp's and Stats
                        </Button>
                    </Link>
                </li>
                <li>
                    <Link href="https://dehnes.com/stats/d/sDbG2Td7k/energy-prices&from=now&to=now+2d">
                        <Button variant="contained" color="primary">
                            Energy prices
                        </Button>
                    </Link>
                </li>
            </ul>

            {/* Temperature last 48Hours */}
            <iframe
                src="/stats/d-solo/yvh6hxW4z/embedded-graphs?orgId=1&refresh=5s&theme=dark&panelId=6&refresh=5s&from=now-48h&to=now"
                style={{
                    border: "none",
                    width: "100%"
                }}></iframe>

            {/* Energy cost 24h */}
            <iframe
                src="/stats/d-solo/yvh6hxW4z/embedded-graphs?orgId=1&theme=dark&panelId=10&refresh=60s"
                style={{
                    border: "none",
                    width: "33%"
                }}></iframe>
            {/* Energy cost month */}
            <iframe
                src="/stats/d-solo/yvh6hxW4z/embedded-graphs?orgId=1&theme=dark&panelId=11&refresh=60s"
                style={{
                    border: "none",
                    width: "33%"
                }}></iframe>

            {/* Energy usage kWh */}
            <iframe
                src="/stats/d-solo/yvh6hxW4z/embedded-graphs?orgId=1&refresh=5s&theme=dark&panelId=8&refresh=60s"
                style={{
                    border: "none",
                    width: "33%"
                }}></iframe>

            {/* Power in Watt */}
            <iframe
                src="/stats/d-solo/yvh6hxW4z/embedded-graphs?orgId=1&theme=dark&panelId=2&refresh=5s&from=now-6h&to=now"
                style={{
                    border: "none",
                    width: "100%"
                }}></iframe>

        </Container>
    );
};

export default App;
