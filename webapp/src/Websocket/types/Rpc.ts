import {Subscribe, Unsubscribe} from "./Subscription";
import {GarageRequest, GarageResponse} from "./Garage";
import {UnderFloorHeaterRequest, UnderFloorHeaterResponse} from "./UnderFloorHeater";
import {EvChargingStationRequest, EvChargingStationResponse} from "./EVChargingStation";

export enum RequestType {
    subscribe = "subscribe",
    unsubscribe = "unsubscribe",

    garageRequest = "garageRequest",
    underFloorHeaterRequest = "underFloorHeaterRequest",
    evChargingStationRequest = "evChargingStationRequest"
}

export class RpcRequest {
    public type: RequestType;
    public subscribe: Subscribe | null;
    public unsubscribe: Unsubscribe | null;

    public garageRequest: GarageRequest | null;
    public underFloorHeaterRequest: UnderFloorHeaterRequest | null;
    public evChargingStationRequest: EvChargingStationRequest | null;

    public constructor(type: RequestType, subscribe: Subscribe | null, unsubscribe: Unsubscribe | null, garageRequest: GarageRequest | null, underFloorHeaterRequest: UnderFloorHeaterRequest | null, evChargingStationRequest: EvChargingStationRequest | null) {
        this.type = type;
        this.subscribe = subscribe;
        this.unsubscribe = unsubscribe;
        this.garageRequest = garageRequest;
        this.underFloorHeaterRequest = underFloorHeaterRequest;
        this.evChargingStationRequest = evChargingStationRequest;
    }
}

export class RpcResponse {
    public subscriptionCreated: boolean | null;
    public subscriptionRemoved: boolean | null;

    public garageResponse: GarageResponse | null;
    public underFloorHeaterResponse: UnderFloorHeaterResponse | null;
    public evChargingStationResponse: EvChargingStationResponse | null;

    public constructor(subscriptionCreated: boolean | null, subscriptionRemoved: boolean | null, garageResponse: GarageResponse | null, underFloorHeaterResponse: UnderFloorHeaterResponse | null, evChargingStationResponse: EvChargingStationResponse | null) {
        this.subscriptionCreated = subscriptionCreated;
        this.subscriptionRemoved = subscriptionRemoved;
        this.garageResponse = garageResponse;
        this.underFloorHeaterResponse = underFloorHeaterResponse;
        this.evChargingStationResponse = evChargingStationResponse;
    }
}
