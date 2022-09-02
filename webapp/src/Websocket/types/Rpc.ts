import {Subscribe, Unsubscribe} from "./Subscription";
import {GarageRequest, GarageResponse} from "./Garage";
import {UnderFloorHeaterRequest, UnderFloorHeaterResponse} from "./UnderFloorHeater";
import {EvChargingStationRequest, EvChargingStationResponse} from "./EVChargingStation";
import { EnvironmentSensorRequest, EnvironmentSensorResponse } from "./EnvironmentSensors";
import { VideoBrowserRequest, VideoBrowserResponse } from "./Video";
import { QuickStatsResponse } from "./QuickStats";

export enum RequestType {
    subscribe = "subscribe",
    unsubscribe = "unsubscribe",

    garageRequest = "garageRequest",
    underFloorHeaterRequest = "underFloorHeaterRequest",
    evChargingStationRequest = "evChargingStationRequest",
    environmentSensorRequest = "environmentSensorRequest",
    videoBrowser = "videoBrowser",
    quickStats = "quickStats",
}

export class RpcRequest {
    public type: RequestType;
    public subscribe: Subscribe | null;
    public unsubscribe: Unsubscribe | null;

    public garageRequest: GarageRequest | null;
    public underFloorHeaterRequest: UnderFloorHeaterRequest | null;
    public evChargingStationRequest: EvChargingStationRequest | null;
    public environmentSensorRequest: EnvironmentSensorRequest | null;
    public videoBrowserRequest: VideoBrowserRequest | null;

    public constructor(type: RequestType, subscribe: Subscribe | null, unsubscribe: Unsubscribe | null, garageRequest: GarageRequest | null, underFloorHeaterRequest: UnderFloorHeaterRequest | null, evChargingStationRequest: EvChargingStationRequest | null, environmentSensorRequest: EnvironmentSensorRequest | null, videoBrowserRequest: VideoBrowserRequest | null) {
        this.type = type;
        this.subscribe = subscribe;
        this.unsubscribe = unsubscribe;
        this.garageRequest = garageRequest;
        this.underFloorHeaterRequest = underFloorHeaterRequest;
        this.evChargingStationRequest = evChargingStationRequest;
        this.environmentSensorRequest = environmentSensorRequest;
        this.videoBrowserRequest = videoBrowserRequest;
    }
}

export class RpcResponse {
    public subscriptionCreated: boolean | null;
    public subscriptionRemoved: boolean | null;

    public garageResponse: GarageResponse | null;
    public underFloorHeaterResponse: UnderFloorHeaterResponse | null;
    public evChargingStationResponse: EvChargingStationResponse | null;
    public environmentSensorResponse: EnvironmentSensorResponse | null;
    public videoBrowserResponse: VideoBrowserResponse | null;
    public quickStatsResponse: QuickStatsResponse | null;

    public constructor(subscriptionCreated: boolean | null, subscriptionRemoved: boolean | null, garageResponse: GarageResponse | null, underFloorHeaterResponse: UnderFloorHeaterResponse | null, evChargingStationResponse: EvChargingStationResponse | null, environmentSensorResponse: EnvironmentSensorResponse | null, videoBrowserResponse: VideoBrowserResponse | null, quickStatsResponse: QuickStatsResponse | null) {
        this.subscriptionCreated = subscriptionCreated;
        this.subscriptionRemoved = subscriptionRemoved;
        this.garageResponse = garageResponse;
        this.underFloorHeaterResponse = underFloorHeaterResponse;
        this.evChargingStationResponse = evChargingStationResponse;
        this.environmentSensorResponse = environmentSensorResponse;
        this.videoBrowserResponse = videoBrowserResponse;
        this.quickStatsResponse = quickStatsResponse;
    }
}
