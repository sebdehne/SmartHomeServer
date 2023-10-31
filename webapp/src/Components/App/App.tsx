import React, { useEffect, useState } from 'react';
import { Button, Container, Paper, Table, TableBody, TableCell, TableContainer, TableRow } from "@mui/material";
import { useNavigate } from "react-router-dom";
import './App.css';
import { QuickStatsResponse } from "../../Websocket/types/QuickStats";
import WebsocketService, { useUserSettings } from "../../Websocket/websocketClient";
import Header from "../Header";
import { numberNok } from "../Utils/utils";
import Icon from '@mdi/react'
import {
    mdiCctv,
    mdiEvStation,
    mdiGarageVariant,
    mdiHeatingCoil,
    mdiHomeBattery,
    mdiHomeThermometer,
    mdiTransmissionTower,
    mdiVhs,
    mdiAccountSupervisor
} from '@mdi/js'

const App = () => {

    const navigate = useNavigate();

    const [quickStatsResponse, setQuickStatsResponse] = useState<QuickStatsResponse | null>(null);
    const userSettings = useUserSettings();

    useEffect(() => {
        const subId = WebsocketService.subscribe("quickStatsEvents", notify => {
                setQuickStatsResponse(notify.quickStatsResponse!!);
            },
            () => WebsocketService.rpc({
                type: "quickStats",
            }).then(response => {
                setQuickStatsResponse(response.quickStatsResponse!!);
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

            <div
                style={{
                    display: "flex",
                    flexDirection: "column"
                }}
            >
                <div style={{ display: "flex", flexDirection: "row" }}>
                    <div style={{ margin: "5px 5px 5px 5px", width: "50%" }}>
                        <Button disabled={!userSettings.userCanRead("garageDoor")} fullWidth variant="contained"
                                color="primary"
                                onClick={relative("/garage")}>
                            <Icon path={mdiGarageVariant} size={3}/>
                        </Button>
                    </div>
                    <div style={{ margin: "5px 5px 5px 5px", width: "50%" }}>
                        <Button disabled={!userSettings.userCanRead("evCharging")} fullWidth variant="contained"
                                color="primary" onClick={relative("/evChargingStations")}>
                            <Icon path={mdiEvStation} size={3}/>
                        </Button>
                    </div>
                </div>

                <div style={{ display: "flex", flexDirection: "row" }}>
                    <div style={{ margin: "5px 5px 5px 5px", width: "50%" }}>
                        <Button disabled={!userSettings.userCanRead("energyStorageSystem")} fullWidth
                                variant="contained" color="primary" onClick={relative("/energy")}>
                            <Icon path={mdiHomeBattery} size={3}/>
                        </Button>
                    </div>
                    <div style={{ margin: "5px 5px 5px 5px", width: "50%" }}>
                        <Button disabled={!userSettings.userCanRead("energyPricing")} fullWidth variant="contained"
                                color="primary"
                                onClick={relative("/energy_price_settings")}>
                            <Icon path={mdiTransmissionTower} size={3}/>
                        </Button>
                    </div>
                </div>

                <div style={{ display: "flex", flexDirection: "row" }}>
                    <div style={{ margin: "5px 5px 5px 5px", width: "50%" }}>
                        <Button disabled={!userSettings.userCanRead("heaterUnderFloor")} fullWidth
                                variant="contained" color="primary" onClick={relative("/heater")}>
                            <div style={{display: "flex", flexDirection: "column", alignItems: "center"}}>
                            <Icon path={mdiHeatingCoil} size={3}/>
                                <span>First floor</span>
                            </div>
                        </Button>
                    </div>
                    <div style={{ margin: "5px 5px 5px 5px", width: "50%" }}>
                        <Button disabled={!userSettings.userCanRead("heaterStairs")} fullWidth
                                variant="contained" color="primary" onClick={relative("/stairs")}>
                            <div style={{display: "flex", flexDirection: "column", alignItems: "center"}}>
                                <Icon path={mdiHeatingCoil} size={3}/>
                                <span>Stairs</span>
                            </div>
                        </Button>
                    </div>
                </div>

                <div style={{ display: "flex", flexDirection: "row" }}>
                    <div style={{ margin: "5px 5px 5px 5px", width: "50%" }}>
                        <Button disabled={!userSettings.userCanRead("environmentSensors")} fullWidth
                                variant="contained" color="primary" onClick={relative("/environmentSensors")}>
                            <Icon path={mdiHomeThermometer} size={3}/>
                        </Button>
                    </div>
                    <div style={{ margin: "5px 5px 5px 5px", width: "50%" }}>
                        <Button disabled={!userSettings.userCanRead("cameras")} fullWidth variant="contained"
                                color="primary" onClick={relative("/webcams")}>
                            <Icon path={mdiCctv} size={3}/>
                        </Button>
                    </div>
                </div>

                <div style={{ display: "flex", flexDirection: "row" }}>
                    <div style={{ margin: "5px 5px 5px 5px", width: "50%" }}>
                        <Button disabled={!userSettings.userCanRead("recordings")} fullWidth variant="contained"
                                color="primary" onClick={relative("/recordings")}>
                            <Icon path={mdiVhs} size={3}/>
                        </Button>
                    </div>
                    <div style={{ margin: "5px 5px 5px 5px", width: "50%" }}>
                        <Button disabled={!userSettings.userCanRead("userSettings")} fullWidth variant="contained"
                                color="primary" onClick={relative("/users")}>
                            <Icon path={mdiAccountSupervisor} size={3}/>
                        </Button>
                    </div>
                </div>


            </div>


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
                                <TableCell component="th" scope="row">ESS State</TableCell>
                                <TableCell align="right"
                                           style={otherStats()}>{quickStatsResponse.essSystemStatus}</TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row">ESS Battery Power</TableCell>
                                <TableCell align="right"
                                           style={otherStats()}>{quickStatsResponse.essBatteryPower} Watt</TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row">ESS Battery SoC</TableCell>
                                <TableCell align="right"
                                           style={otherStats()}>{quickStatsResponse.essBatterySoC} %</TableCell>
                            </TableRow>


                            <TableRow>
                                <TableCell component="th" scope="row">
                                    Cost imported energy today</TableCell>
                                <TableCell align="right"
                                           style={otherStats()}>{numberNok(quickStatsResponse.costEnergyImportedToday)}</TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row"
                                           onClick={() => window.open("https://dehnes.com/stats/d/sDbG2Td7k/energy-prices&from=now&to=now+2d", "_parent")}>
                                    Current Energy price</TableCell>
                                <TableCell align="right"
                                           style={otherStats()}>{numberNok(quickStatsResponse.currentEnergyPrice)} /
                                    kWh</TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row">Cost imported energy this month</TableCell>
                                <TableCell align="right"
                                           style={otherStats()}>{numberNok(quickStatsResponse.costEnergyImportedCurrentMonth)}</TableCell>
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

