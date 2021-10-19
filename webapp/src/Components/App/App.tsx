import React from 'react';
import { Button, Container, Link} from "@material-ui/core";
import { useHistory } from "react-router-dom";
import './App.css';

const App = () => {

    let history = useHistory();

    const relative = (path: string) => () => {
        history.push(path);
    };

    return (
        <Container maxWidth="sm" className="App">
            <h2 style={{textAlign: "center"}}>Smart home controller</h2>
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
                    <Link href="https://dehnes.com/stats/d/sDbG2Td7k/energy-prices">
                        <Button variant="contained" color="primary">
                            Energy prices
                        </Button>
                    </Link>
                </li>
            </ul>
        </Container>
    );
};

export default App;
