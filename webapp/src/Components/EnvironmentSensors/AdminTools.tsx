import { Button, Grid, Paper, Table, TableBody, TableCell, TableContainer, TableRow } from "@material-ui/core";
import React from "react";
import WebsocketService from "../../Websocket/websocketClient";
import { RequestType, RpcRequest } from "../../Websocket/types/Rpc";
import {
    EnvironmentSensorRequest,
    EnvironmentSensorRequestType,
    FirmwareInfo
} from "../../Websocket/types/EnvironmentSensors";
import { arrayBufferToBase64 } from "../Utils/utils";

type AdminToolsProps = {
    firmwareInfo: FirmwareInfo | null;
    setSending: (sending: boolean) => void;
    setCmdResult: (sending: boolean | null) => void;
    setFirmwareInfo: (firmwareInfo: FirmwareInfo | null) => void;
    sendUpdate: (req: EnvironmentSensorRequest) => void;
}

export const AdminTools = ({ firmwareInfo, setSending, setCmdResult, setFirmwareInfo, sendUpdate }: AdminToolsProps) => {

    const uploadFirmware = (file: File) => {
        setSending(true);

        const reader = new FileReader();

        reader.onload = ev => {
            const rawData = ev!!.target!!.result as ArrayBuffer;
            const firmwareBased64Encoded = arrayBufferToBase64(rawData);

            WebsocketService.rpc(new RpcRequest(
                RequestType.environmentSensorRequest,
                null,
                null,
                null,
                null,
                null,
                new EnvironmentSensorRequest(
                    EnvironmentSensorRequestType.uploadFirmware,
                    null,
                    file.name,
                    firmwareBased64Encoded,
                    null
                ),
                null
            )).then(response => {
                setCmdResult(true);
                setFirmwareInfo(response.environmentSensorResponse!!.firmwareInfo);
                setTimeout(() => {
                    setCmdResult(null);
                }, 2000);
            }).finally(() => setSending(false));

        };
        reader.onerror = ev => {
            console.log("Error reading file:");
            console.log(ev);
        };
        reader.readAsArrayBuffer(file);
    };

    const scheduleTimeAdjustment = () => sendUpdate(new EnvironmentSensorRequest(
        EnvironmentSensorRequestType.scheduleTimeAdjustment,
        null,
        null,
        null,
        null
    ));
    const scheduleReset = () => sendUpdate(new EnvironmentSensorRequest(
        EnvironmentSensorRequestType.scheduleReset,
        null,
        null,
        null,
        null
    ));

    return (
        <Grid item xs={12}>
            <Grid container justify="flex-start" spacing={2} alignItems={"center"}>
                <TableContainer component={Paper} style={{
                    marginTop: "20px"
                }}>
                    <Table aria-label="simple table">
                        <TableBody>
                            {firmwareInfo &&
                            <>
                                <TableRow>
                                    <TableCell component="th" scope="row">Filename</TableCell>
                                    <TableCell align="right">{firmwareInfo.filename}</TableCell>
                                </TableRow>
                                <TableRow>
                                    <TableCell component="th" scope="row">Size</TableCell>
                                    <TableCell align="right">{firmwareInfo.size} bytes</TableCell>
                                </TableRow>
                            </>
                            }
                            <TableRow>
                                <TableCell component="th" scope="row">Upload new</TableCell>
                                <TableCell align="right">
                                    <input
                                        accept=".bin"
                                        hidden
                                        id="firmwareUploadFileSelector"
                                        type="file"
                                        onChange={e => {
                                            uploadFirmware(e.target.files!![0]);
                                        }}
                                    />
                                    <label htmlFor="firmwareUploadFileSelector">
                                        <Button variant="contained" component="span">
                                            Upload firmware
                                        </Button>
                                    </label>
                                </TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row"/>
                                <TableCell align="right">
                                    <Button variant="contained" component="span" onClick={scheduleTimeAdjustment}>
                                        Adjust time for all
                                    </Button>
                                </TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell component="th" scope="row"/>
                                <TableCell align="right">
                                    <Button variant="contained" component="span" onClick={scheduleReset}>
                                        Reset all
                                    </Button>
                                </TableCell>
                            </TableRow>
                        </TableBody>
                    </Table>
                </TableContainer>
            </Grid>
        </Grid>
    );
};