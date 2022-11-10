import { Button } from "@material-ui/core";
import React from "react";
import WebsocketService from "../../Websocket/websocketClient";
import { RequestType, RpcRequest } from "../../Websocket/types/Rpc";
import { arrayBufferToBase64 } from "../Utils/utils";
import { GarageRequest, GarageRequestType } from "../../Websocket/types/Garage";

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
                RequestType.garageRequest,
                null,
                null,
                new GarageRequest(
                    GarageRequestType.firmwareUpgrade,
                    null,
                    firmwareBased64Encoded
                ),
                null,
                null,
                null,
                null,
                null,
            )).then(response => {
                setCmdResult(response.garageResponse!!.firmwareUploadSuccess!!);
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