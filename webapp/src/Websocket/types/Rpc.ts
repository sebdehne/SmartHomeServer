import {Subscribe, Unsubscribe} from "./Subscription";
import {GarageRequest, GarageResponse} from "./Garage";
import {UnderFloorHeaterRequest, UnderFloorHeaterResponse} from "./UnderFloorHeater";
import {EvChargingStationRequest, EvChargingStationResponse} from "./EVChargingStation";
import { EnvironmentSensorRequest, EnvironmentSensorResponse } from "./EnvironmentSensors";
import { VideoBrowserRequest, VideoBrowserResponse } from "./Video";
import { QuickStatsResponse } from "./QuickStats";
import { EssRequest, EssValues } from "./Ess";

export enum RequestType {
    subscribe = "subscribe",
    unsubscribe = "unsubscribe",

    garageRequest = "garageRequest",
    underFloorHeaterRequest = "underFloorHeaterRequest",
    evChargingStationRequest = "evChargingStationRequest",
    environmentSensorRequest = "environmentSensorRequest",
    videoBrowser = "videoBrowser",
    quickStats = "quickStats",
    essValues = "essValues",
    essRequest = "essRequest",
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
    public essRequest: EssRequest | null;

    public constructor(type: RequestType, subscribe: Subscribe | null, unsubscribe: Unsubscribe | null, garageRequest: GarageRequest | null, underFloorHeaterRequest: UnderFloorHeaterRequest | null, evChargingStationRequest: EvChargingStationRequest | null, environmentSensorRequest: EnvironmentSensorRequest | null, videoBrowserRequest: VideoBrowserRequest | null, essRequest: EssRequest | null) {
        this.type = type;
        this.subscribe = subscribe;
        this.unsubscribe = unsubscribe;
        this.garageRequest = garageRequest;
        this.underFloorHeaterRequest = underFloorHeaterRequest;
        this.evChargingStationRequest = evChargingStationRequest;
        this.environmentSensorRequest = environmentSensorRequest;
        this.videoBrowserRequest = videoBrowserRequest;
        this.essRequest = essRequest;
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
    public essValues: EssValues | null;

    public constructor(subscriptionCreated: boolean | null, subscriptionRemoved: boolean | null, garageResponse: GarageResponse | null, underFloorHeaterResponse: UnderFloorHeaterResponse | null, evChargingStationResponse: EvChargingStationResponse | null, environmentSensorResponse: EnvironmentSensorResponse | null, videoBrowserResponse: VideoBrowserResponse | null, quickStatsResponse: QuickStatsResponse | null, essValues: EssValues | null) {
        this.subscriptionCreated = subscriptionCreated;
        this.subscriptionRemoved = subscriptionRemoved;
        this.garageResponse = garageResponse;
        this.underFloorHeaterResponse = underFloorHeaterResponse;
        this.evChargingStationResponse = evChargingStationResponse;
        this.environmentSensorResponse = environmentSensorResponse;
        this.videoBrowserResponse = videoBrowserResponse;
        this.quickStatsResponse = quickStatsResponse;
        this.essValues = essValues;
    }
}
