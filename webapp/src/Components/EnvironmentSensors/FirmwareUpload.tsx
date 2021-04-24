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

type FirmwareUploadProps = {
    firmwareInfo: FirmwareInfo | null;
    setSending: (sending: boolean) => void;
    setCmdResult: (sending: boolean | null) => void;
    setFirmwareInfo: (firmwareInfo: FirmwareInfo | null) => void;
}

export const FirmwareUpload = ({ firmwareInfo, setSending, setCmdResult, setFirmwareInfo }: FirmwareUploadProps) => {

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
                )
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
                        </TableBody>
                    </Table>
                </TableContainer>
            </Grid>
        </Grid>
    );
};