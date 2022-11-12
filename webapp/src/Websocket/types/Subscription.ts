import { GarageResponse } from "./Garage";
import { UnderFloorHeaterResponse } from "./UnderFloorHeater";
import { EvChargingEvent } from "./EVChargingStation";
import { EnvironmentSensorEvent } from "./EnvironmentSensors";
import { QuickStatsResponse } from "./QuickStats";
import { ESSState } from "./EnergyStorageSystem";

export type SubscriptionType =
    "getGarageStatus"
    | "getUnderFloorHeaterStatus"
    | "evChargingStationEvents"
    | "environmentSensorEvents"
    | "quickStatsEvents"
    | "essState";

export type Subscribe = {
    subscriptionId: string;
    type: SubscriptionType;
}

export type Unsubscribe = {
    subscriptionId: string;
}

export type Notify = {
    subscriptionId: string;
    garageStatus?: GarageResponse;
    underFloorHeaterStatus?: UnderFloorHeaterResponse;
    evChargingStationEvent?: EvChargingEvent;
    environmentSensorEvent?: EnvironmentSensorEvent;
    quickStatsResponse?: QuickStatsResponse;
    essState?: ESSState;
}

