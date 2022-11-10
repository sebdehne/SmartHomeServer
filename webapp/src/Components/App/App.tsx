import React, { useEffect, useState } from 'react';
import { Button, Container, Paper, Table, TableBody, TableCell, TableContainer, TableRow } from "@material-ui/core";
import { useNavigate } from "react-router-dom";
import './App.css';
import { QuickStatsResponse } from "../../Websocket/types/QuickStats";
import WebsocketService from "../../Websocket/websocketClient";
import { RequestType, RpcRequest } from "../../Websocket/types/Rpc";
import Header from "../Header";
import { SubscriptionType } from "../../Websocket/types/Subscription";

const App = () => {

    const navigate = useNavigate();

    const [quickStatsResponse, setQuickStatsResponse] = useState<QuickStatsResponse | null>(null);

    useEffect(() => {
        const subId = WebsocketService.subscribe(SubscriptionType.quickStatsEvents, notify => {
                setQuickStatsResponse(notify.quickStatsResponse);
            },
            () => WebsocketService.rpc(new RpcRequest(
                RequestType.quickStats,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
            )).then(response => {
                setQuickStatsResponse(response.quickStatsResponse);
            }));

        return () => WebsocketService.unsubscribe(subId);
    }, []);

    const relative = (path: string) => () => {
        navigate(path);
    };

    const calcTemperatureStyle = (outsideTemperature: number): React.CSSProperties | undefined => {
        const color = outsideTemperature < 0
            ? "#04afff"
            : "#beff04";
        return {
            color,
            fontWeight: 'bold',
            fontSize: "20px"
        };
    };
    const calcPowerStyle = (powerImportInWatts: number): React.CSSProperties | undefined => {
        let color = "#ff2020";
        if (powerImportInWatts < 4000) {
            color = "#ffd620";
        }
        if (powerImportInWatts < 1000) {
            color = "#49ff1f";
        }
        return {
            color,
            fontWeight: 'bold',
            fontSize: "20px"
        };
    };

    const otherStats = (): React.CSSProperties | undefined => {
        return {
            fontWeight: 'bold',
            fontSize: "20px"
        };
    };


    return (
        <Container maxWidth="sm" className="App">
            <Header
                title="Smart home controller"
                sending={false}
                showBackButton={false}
            />
            <ul className="Menu">
                <li>
                    <Button variant="contained" color="primary" onClick={relative("/garage")}>
                        Garage door
                    </Button>
                </li>
                <li>
                    <Button variant="contained" color="primary" onClick={relative("/energy")}>
                        Energy Storge System
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
            </ul>

            {quickStatsResponse &&
                <TableContainer component={Paper} style={{
                    marginTop: "20px"
                }}>
                    <Table aria-label="simple table">
                        <TableBody>
                            <TableRow>
                                <TableCell component="th" scope="row"
                                           onClick={() => window.open("https://dehnes.com/stats/d/000000007/current", "_parent")}>
                                    Outside temperature
                                </TableCell>
                                <TableCell align="right"
                                           style={calcTemperatureStyle(quickStatsResponse.outsideTemperature)}>
                                    {new Intl.NumberFormat('nb-NO', {
                                        style: 'unit',
                                        unit: 'celsius'
                                    }).format(quickStatsResponse.outsideTemperature)}
                                </TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row"
                                           onClick={() => window.open("https://dehnes.com/stats/d/MpeDjbZ4z/electricity?orgId=1&refresh=5s", "_parent")}>
                                    Current import power</TableCell>
                                <TableCell align="right"
                                           style={calcPowerStyle(quickStatsResponse.powerImportInWatts)}>{quickStatsResponse.powerImportInWatts} Watt</TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row">
                                    Current export power</TableCell>
                                <TableCell align="right"
                                           style={calcPowerStyle(quickStatsResponse.powerExportInWatts)}>{quickStatsResponse.powerExportInWatts} Watt</TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row">
                                    Cost imported energy today</TableCell>
                                <TableCell align="right"
                                           style={otherStats()}>{new Intl.NumberFormat('nb-NO', {
                                    style: 'currency',
                                    currency: 'NOK'
                                }).format(quickStatsResponse.costEnergyImportedToday)}</TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row"
                                           onClick={() => window.open("https://dehnes.com/stats/d/sDbG2Td7k/energy-prices&from=now&to=now+2d", "_parent")}>
                                    Current Energy price</TableCell>
                                <TableCell align="right"
                                           style={otherStats()}>{new Intl.NumberFormat('nb-NO', {
                                    style: 'currency',
                                    currency: 'NOK'
                                }).format(quickStatsResponse.currentEnergyPrice)} / kWh</TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row">Cost imported energy current month</TableCell>
                                <TableCell align="right"
                                           style={otherStats()}>{new Intl.NumberFormat('nb-NO', {
                                    style: 'currency',
                                    currency: 'NOK'
                                }).format(quickStatsResponse.costEnergyImportedCurrentMonth)}</TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row">Energy imported today</TableCell>
                                <TableCell
                                    align="right"
                                    style={otherStats()}>{new Intl.NumberFormat('nb-NO', { maximumFractionDigits: 1 }).format(quickStatsResponse.energyImportedTodayWattHours / 1000)} kWh</TableCell>
                            </TableRow>
                        </TableBody>
                    </Table>
                </TableContainer>
            }

        </Container>
    );
};

export default App;

