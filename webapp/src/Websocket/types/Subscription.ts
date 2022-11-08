import { GarageResponse } from "./Garage";
import { UnderFloorHeaterResponse } from "./UnderFloorHeater";
import { EvChargingEvent } from "./EVChargingStation";
import { EnvironmentSensorEvent } from "./EnvironmentSensors";
import { QuickStatsResponse } from "./QuickStats";
import { EssValues } from "./Ess";

export enum SubscriptionType {
    getGarageStatus = "getGarageStatus",
    getUnderFloorHeaterStatus = "getUnderFloorHeaterStatus",
    evChargingStationEvents = "evChargingStationEvents",
    environmentSensorEvents = "environmentSensorEvents",
    quickStatsEvents = "quickStatsEvents",
    essValues = "essValues",
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
    public underFloorHeaterStatus: UnderFloorHeaterResponse | null;
    public evChargingStationEvent: EvChargingEvent | null;
    public environmentSensorEvent: EnvironmentSensorEvent | null;
    public quickStatsResponse: QuickStatsResponse | null;
    public essValues: EssValues | null;

    public constructor(subscriptionId: string, garageStatus: GarageResponse | null, underFloorHeaterStatus: UnderFloorHeaterResponse | null, evChargingStationEvent: EvChargingEvent | null, environmentSensorEvent: EnvironmentSensorEvent | null, quickStatsResponse: QuickStatsResponse | null, essValues: EssValues | null) {
        this.subscriptionId = subscriptionId;
        this.garageStatus = garageStatus;
        this.underFloorHeaterStatus = underFloorHeaterStatus;
        this.evChargingStationEvent = evChargingStationEvent;
        this.environmentSensorEvent = environmentSensorEvent;
        this.quickStatsResponse = quickStatsResponse;
        this.essValues = essValues;
    }
}

