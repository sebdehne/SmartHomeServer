import { Button } from "@material-ui/core";
import React from "react";
import WebsocketService from "../../Websocket/websocketClient";
import { RequestType, RpcRequest } from "../../Websocket/types/Rpc";
import { EnvironmentSensorRequest, EnvironmentSensorRequestType } from "../../Websocket/types/EnvironmentSensors";
import { arrayBufferToBase64 } from "../Utils/utils";

type FirmwareUploadProps = {
    setSending: (sending: boolean) => void;
    setCmdResult: (sending: boolean | null) => void;
}

export const FirmwareUpload = ({ setSending, setCmdResult }: FirmwareUploadProps) => {

    const uploadFirmware = (file: File) => {
        setSending(true);

        const reader = new FileReader();

        reader.onload = ev => {
            const rawData = ev!!.target!!.result as ArrayBuffer;
            const firmwareBased64Encoded = arrayBufferToBase64(rawData);

            WebsocketService.rpc(new RpcRequest(
                RequestType.evChargingStationRequest,
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
                setCmdResult(response.evChargingStationResponse!!.uploadFirmwareToClientResult!!);
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
        <div>
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
        </div>
    );
};