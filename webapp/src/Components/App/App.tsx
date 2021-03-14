import React from 'react';
import { Button, Container, Link, Paper, Typography } from "@material-ui/core";
import { useHistory } from "react-router-dom";

const App = () => {

    let history = useHistory();

    const relative = (path: string) => () => {
        history.push(path);
    };

    return (
        <Container maxWidth="sm" className="App">
            <Paper>
                <Typography>
                    <h2>Smart home controller</h2>
                </Typography>
                <ul>
                    <li>
                        <Button variant="contained" color="secondary" onClick={relative("/garage")}>
                            Garage door
                        </Button>
                    </li>
                    <li>
                        <Button variant="contained" color="secondary" onClick={relative("/heater")}>
                            Heater under floor
                        </Button>
                    </li>
                    <li>
                        <Button variant="contained" color="secondary" onClick={relative("/evChargingStations")}>
                            EV Charging
                        </Button>
                    </li>
                    <li>
                        <Button variant="contained" color="secondary" onClick={relative("/webcams")}>
                            Cameras
                        </Button>
                    </li>
                    <li>
                        <Link href="https://dehnes.com/stats/d/000000007/current">
                            <Button variant="contained" color="secondary">
                                Temp's and Stats
                            </Button>
                        </Link>
                    </li>
                </ul>
            </Paper>
        </Container>
    );
};

export default App;
