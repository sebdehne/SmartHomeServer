import {GarageStatus} from "./Garage";
import {UnderFloorHeaterStatus} from "./UnderFloorHeater";
import {Event} from "./EVChargingStation";

export enum SubscriptionType {
    getGarageStatus = "getGarageStatus",
    getUnderFloorHeaterStatus = "getUnderFloorHeaterStatus",
    evChargingStationConnections = "evChargingStationConnections"
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
    public garageStatus: GarageStatus | null;
    public underFloorHeaterStatus: UnderFloorHeaterStatus | null;
    public evChargingStationEvent: Event | null;

    public constructor(subscriptionId: string, garageStatus: GarageStatus | null, underFloorHeaterStatus: UnderFloorHeaterStatus | null, evChargingStationEvent: Event | null) {
        this.subscriptionId = subscriptionId;
        this.garageStatus = garageStatus;
        this.underFloorHeaterStatus = underFloorHeaterStatus;
        this.evChargingStationEvent = evChargingStationEvent;
    }
}

