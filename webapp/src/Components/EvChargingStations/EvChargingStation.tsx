import React, { useState } from "react";
import {
    EvChargingMode,
    EvChargingStationClient,
    EvChargingStationDataAndConfig,
    EvChargingStationRequest,
    EvChargingStationRequestType,
    LoadSharingPriority,
    ProximityPilotAmps
} from "../../Websocket/types/EVChargingStation";
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Button,
    ButtonGroup,
    Grid,
    Paper,
    Switch,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography
} from "@material-ui/core";
import ExpandMoreIcon from '@material-ui/icons/ExpandMore';
import WebsocketService from "../../Websocket/websocketClient";
import { RequestType, RpcRequest } from "../../Websocket/types/Rpc";
import { timeToDelta } from "../GarageDoor/GarageDoor";
import { FirmwareUpload } from "./FirmwareUpload";

type EvChargingStationProps = {
    setSending: (sending: boolean) => void;
    setCmdResult: (sending: boolean | null) => void;
    station: EvChargingStationDataAndConfig;
    setStations: (stations: EvChargingStationDataAndConfig[]) => void;
    currentSeconds: number;
};

export const EvChargingStation = ({
                                      setSending,
                                      setCmdResult,
                                      station,
                                      setStations,
                                      currentSeconds
                                  }: EvChargingStationProps) => {

    const [showData, setShowData] = useState<boolean>(false);

    const sendUpdate = (req: EvChargingStationRequest) => {
        setSending(true);
        WebsocketService.rpc(new RpcRequest(
            RequestType.evChargingStationRequest,
            null,
            null,
            null,
            null,
            req))
            .then(response => {
                setCmdResult(response.evChargingStationResponse!!.configUpdated!!);
                setStations(response.evChargingStationResponse!!.chargingStationsDataAndConfig);
                setTimeout(() => {
                    setCmdResult(null);
                }, 2000);
            })
            .finally(() => setSending(false));
    };

    const updateModeTo = (mode: EvChargingMode) => sendUpdate(new EvChargingStationRequest(
        EvChargingStationRequestType.setMode,
        station.clientConnection.clientId,
        null,
        mode,
        null,
        null
    ));
    const updatePriorityTo = (priority: LoadSharingPriority) => sendUpdate(new EvChargingStationRequest(
        EvChargingStationRequestType.setLoadSharingPriority,
        station.clientConnection.clientId,
        null,
        null,
        priority,
        null
    ));
    const changeNumberOfHoursRequiredFor = (delta: number) => sendUpdate(
        new EvChargingStationRequest(
            EvChargingStationRequestType.setNumberOfHoursRequiredFor,
            station.clientConnection.clientId,
            null,
            null,
            null,
            station.config.numberOfHoursRequiredFor + delta
        )
    );

    return <Accordion key={station.clientConnection.clientId}>
        <AccordionSummary
            expandIcon={<ExpandMoreIcon/>}
            aria-controls="panel1a-content"
            id="panel1a-header"
        >
            <Typography>{station.clientConnection.displayName} - {station.data.chargingState} (since: {timeToDelta(currentSeconds, station.data.chargingStateChangedAt)})</Typography>
        </AccordionSummary>
        <AccordionDetails>

            <Grid container spacing={2}>
                <Grid item xs={12}>
                    <Grid container justify="flex-start" spacing={2}>
                        <ButtonGroup variant="contained" aria-label="contained primary button group" style={{
                            margin: "10px"
                        }}>
                            <Button
                                color={station.config.mode === EvChargingMode.ON ? 'primary' : 'secondary'}
                                onClick={() => updateModeTo(EvChargingMode.ON)}>On</Button>
                            <Button color={station.config.mode === EvChargingMode.OFF ? 'primary' : 'secondary'}
                                    onClick={() => updateModeTo(EvChargingMode.OFF)}>Off</Button>
                            <Button
                                color={station.config.mode === EvChargingMode.ChargeDuringCheapHours ? 'primary' : 'secondary'}
                                onClick={() => updateModeTo(EvChargingMode.ChargeDuringCheapHours)}
                            >Low-cost</Button>
                        </ButtonGroup>
                    </Grid>
                </Grid>
                <Grid item xs={12}>
                    <Grid container justify="flex-start" spacing={2}>

                        <ButtonGroup variant="contained" aria-label="contained primary button group" style={{
                            margin: "10px"
                        }}>
                            <Button
                                color={station.config.loadSharingPriority === LoadSharingPriority.HIGH ? 'primary' : 'secondary'}
                                onClick={() => updatePriorityTo(LoadSharingPriority.HIGH)}>High priority</Button>
                            <Button
                                color={station.config.loadSharingPriority === LoadSharingPriority.NORMAL ? 'primary' : 'secondary'}
                                onClick={() => updatePriorityTo(LoadSharingPriority.NORMAL)}>Normal priority</Button>
                            <Button
                                color={station.config.loadSharingPriority === LoadSharingPriority.LOW ? 'primary' : 'secondary'}
                                onClick={() => updatePriorityTo(LoadSharingPriority.LOW)}>Low priority</Button>
                        </ButtonGroup>

                    </Grid>
                </Grid>
                {station.config.mode === EvChargingMode.ChargeDuringCheapHours &&

                <Grid item xs={12}>
                    <Grid container justify="flex-start" spacing={2} alignItems={"center"}>

                        <Grid item xs={4}>
                            <span>Minimum hours/ day: </span>
                            <span>{station.config.numberOfHoursRequiredFor}</span>
                        </Grid>
                        <Grid item xs={8}>
                            <ButtonGroup variant="contained" aria-label="contained primary button group" style={{
                                margin: "10px"
                            }}>
                                <Button
                                    disabled={station.config.numberOfHoursRequiredFor <= 1}
                                    onClick={() => changeNumberOfHoursRequiredFor(-1)}>-</Button>
                                <Button
                                    disabled={station.config.numberOfHoursRequiredFor >= 24}
                                    onClick={() => changeNumberOfHoursRequiredFor(1)}>+</Button>
                            </ButtonGroup>
                        </Grid>
                    </Grid>
                </Grid>

                }

                <Grid item xs={12}>
                    <Grid container justify="flex-start" spacing={2} alignItems={"center"}>
                        <Switch
                            checked={showData}
                            onChange={e => setShowData(e.target.checked)}
                            name="showData"
                            inputProps={{ 'aria-label': 'secondary checkbox' }}
                        />
                        {showData && 'Hide details'}
                        {!showData && 'Show details'}
                    </Grid>
                </Grid>

                {showData &&
                <>
                    <Grid item xs={12}>
                        <Grid container justify="flex-start" spacing={2} alignItems={"center"}>
                            <PowerComponent
                                phase1volts={station.data.phase1Millivolts / 1000}
                                phase2volts={station.data.phase2Millivolts / 1000}
                                phase3volts={station.data.phase3Millivolts / 1000}
                                phase1amps={station.data.phase1Milliamps / 1000}
                                phase2amps={station.data.phase2Milliamps / 1000}
                                phase3amps={station.data.phase3Milliamps / 1000}
                            />
                        </Grid>
                    </Grid>

                    <Grid item xs={12}>
                        <Grid container justify="flex-start" spacing={2} alignItems={"center"}>
                            <StationsDetails
                                maxChargingRate={station.data.maxChargingRate}
                                proximityPilotAmps={station.data.proximityPilotAmps}
                                reasonChargingUnavailable={station.data.reasonChargingUnavailable}
                                systemUptime={station.data.systemUptime}
                                utcTimestampInMs={station.data.utcTimestampInMs}
                                wifiRSSI={station.data.wifiRSSI}
                                currentSeconds={currentSeconds}
                                clientConnection={station.clientConnection}
                                setSending={setSending}
                                setCmdResult={setCmdResult}
                            />
                        </Grid>
                    </Grid>
                </>
                }

            </Grid>
        </AccordionDetails>
    </Accordion>;
};

type StationsDetailsProps = {
    proximityPilotAmps: ProximityPilotAmps;
    reasonChargingUnavailable: string | null;
    maxChargingRate: number;
    systemUptime: number;
    wifiRSSI: number;
    utcTimestampInMs: number;
    currentSeconds: number;
    clientConnection: EvChargingStationClient;
    setSending: (sending: boolean) => void;
    setCmdResult: (sending: boolean | null) => void;
};

const StationsDetails = (props: StationsDetailsProps) => {
    return <TableContainer component={Paper} style={{
        marginTop: "20px"
    }}>
        <Table aria-label="simple table">
            <TableBody>
                <TableRow>
                    <TableCell component="th" scope="row">Connected cable type</TableCell>
                    <TableCell align="right">{props.proximityPilotAmps}</TableCell>
                </TableRow>
                {props.reasonChargingUnavailable && <TableRow>
                    <TableCell component="th" scope="row">reason unavailable</TableCell>
                    <TableCell align="right">{props.reasonChargingUnavailable}</TableCell>
                </TableRow>}
                <TableRow>
                    <TableCell component="th" scope="row">Max charging rate</TableCell>
                    <TableCell align="right">{props.maxChargingRate}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">System Uptime</TableCell>
                    <TableCell align="right">{Math.round(props.systemUptime / 1000 / 60)} minutes</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">WiFi RSSI</TableCell>
                    <TableCell align="right">{props.wifiRSSI}dB</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">Connected power line</TableCell>
                    <TableCell align="right">{props.clientConnection.powerConnectionId}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">TCP endpoint</TableCell>
                    <TableCell align="right">{props.clientConnection.addr}:{props.clientConnection.port}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">TCP connection uptime</TableCell>
                    <TableCell
                        align="right">{timeToDelta(props.currentSeconds, props.clientConnection.connectedSince)}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">Updated</TableCell>
                    <TableCell align="right">{timeToDelta(props.currentSeconds, props.utcTimestampInMs)} ago</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">Firmware version</TableCell>
                    <TableCell align="right">{props.clientConnection.firmwareVersion}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">Firmware upgrade</TableCell>
                    <TableCell align="right">
                        <FirmwareUpload
                            setSending={props.setSending}
                            setCmdResult={props.setCmdResult}
                            clientId={props.clientConnection.clientId}/>
                    </TableCell>
                </TableRow>

            </TableBody>
        </Table>
    </TableContainer>;
};

type PowerComponentProps = {
    phase1volts: number,
    phase2volts: number,
    phase3volts: number,
    phase1amps: number,
    phase2amps: number,
    phase3amps: number
};

const PowerComponent = (props: PowerComponentProps) => {

    let totalPower = props.phase1volts * props.phase1amps + props.phase2volts * props.phase2amps + props.phase3volts * props.phase3amps;

    return <TableContainer component={Paper} style={{
        marginTop: "20px"
    }}>
        <Table aria-label="simple table">
            <TableHead>
                <TableRow>
                    <TableCell/>
                    <TableCell align="right">Voltage (V)</TableCell>
                    <TableCell align="right">Current (A)</TableCell>
                    <TableCell align="right">Power (Watt)</TableCell>
                </TableRow>
            </TableHead>
            <TableBody>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Total
                    </TableCell>
                    <TableCell align="right"/>
                    <TableCell align="right"/>
                    <TableCell
                        align="right">{totalPower.toFixed(2)}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Phase 1
                    </TableCell>
                    <TableCell align="right">{props.phase1volts.toFixed(2)}</TableCell>
                    <TableCell align="right">{props.phase1amps.toFixed(2)}</TableCell>
                    <TableCell align="right">{(props.phase1amps * props.phase1volts).toFixed(2)}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Phase 2
                    </TableCell>
                    <TableCell align="right">{props.phase2volts.toFixed(2)}</TableCell>
                    <TableCell align="right">{props.phase2amps.toFixed(2)}</TableCell>
                    <TableCell align="right">{(props.phase2amps * props.phase2volts).toFixed(2)}</TableCell>
                </TableRow>
                <TableRow>
                    <TableCell component="th" scope="row">
                        Phase 3
                    </TableCell>
                    <TableCell align="right">{props.phase3volts.toFixed(2)}</TableCell>
                    <TableCell align="right">{props.phase3amps.toFixed(2)}</TableCell>
                    <TableCell align="right">{(props.phase3amps * props.phase3volts).toFixed(2)}</TableCell>
                </TableRow>
            </TableBody>
        </Table>
    </TableContainer>;
};