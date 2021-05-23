import { GarageResponse, GarageStatus } from "./Garage";
import {UnderFloorHeaterStatus} from "./UnderFloorHeater";
import {EvChargingEvent} from "./EVChargingStation";
import { EnvironmentSensorEvent } from "./EnvironmentSensors";

export enum SubscriptionType {
    getGarageStatus = "getGarageStatus",
    getUnderFloorHeaterStatus = "getUnderFloorHeaterStatus",
    evChargingStationEvents = "evChargingStationEvents",
    environmentSensorEvents = "environmentSensorEvents"
}

export class Subscribe {
    public subscriptionId: string;
    public type: SubscriptionType;

    public constructor(subscriptionId: string, type: SubscriptionType) {
        this.subscriptionId = subscriptionId;
        this.type = type;
    }
}

export class Unsubscribe {
    public subscriptionId: string;

    public constructor(subscriptionId: string) {
        this.subscriptionId = subscriptionId;
    }
}

export class Notify {
    public subscriptionId: string;
    public garageStatus: GarageResponse | null;
    public underFloorHeaterStatus: UnderFloorHeaterStatus | null;
    public evChargingStationEvent: EvChargingEvent | null;
    public environmentSensorEvent: EnvironmentSensorEvent | null;

    public constructor(subscriptionId: string, garageStatus: GarageResponse | null, underFloorHeaterStatus: UnderFloorHeaterStatus | null, evChargingStationEvent: EvChargingEvent | null, environmentSensorEvent: EnvironmentSensorEvent | null) {
        this.subscriptionId = subscriptionId;
        this.garageStatus = garageStatus;
        this.underFloorHeaterStatus = underFloorHeaterStatus;
        this.evChargingStationEvent = evChargingStationEvent;
        this.environmentSensorEvent = environmentSensorEvent;
    }
}

