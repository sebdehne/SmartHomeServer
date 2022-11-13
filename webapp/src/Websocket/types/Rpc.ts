import { Subscribe, Unsubscribe } from "./Subscription";
import { GarageRequest, GarageResponse } from "./Garage";
import { UnderFloorHeaterRequest, UnderFloorHeaterResponse } from "./UnderFloorHeater";
import { EvChargingStationRequest, EvChargingStationResponse } from "./EVChargingStation";
import { EnvironmentSensorRequest, EnvironmentSensorResponse } from "./EnvironmentSensors";
import { VideoBrowserRequest, VideoBrowserResponse } from "./Video";
import { QuickStatsResponse } from "./QuickStats";
import { EnergyPricingSettingsRead, EnergyPricingSettingsWrite } from "./EnergyPricingSettings";
import { ESSState, ESSWrite } from "./EnergyStorageSystem";
import { UserSettings } from "./UserSettings";

export type RequestType =
    "subscribe"
    | "unsubscribe"
    | "garageRequest"
    | "underFloorHeaterRequest"
    | "evChargingStationRequest"
    | "environmentSensorRequest"
    | "videoBrowser"
    | "quickStats"
    | "readEnergyPricingSettings"
    | "writeEnergyPricingSettings"
    | "essRead"
    | "essWrite"
    | "userSettings"
    ;

export type RpcRequest = {
    type: RequestType;
    subscribe?: Subscribe;
    unsubscribe?: Unsubscribe;

    garageRequest?: GarageRequest;
    underFloorHeaterRequest?: UnderFloorHeaterRequest;
    evChargingStationRequest?: EvChargingStationRequest;
    environmentSensorRequest?: EnvironmentSensorRequest;
    videoBrowserRequest?: VideoBrowserRequest;
    essWrite?: ESSWrite;
    energyPricingSettingsWrite?: EnergyPricingSettingsWrite;
}

export type RpcResponse = {
    subscriptionCreated?: boolean;
    subscriptionRemoved?: boolean;

    garageResponse?: GarageResponse;
    underFloorHeaterResponse?: UnderFloorHeaterResponse;
    evChargingStationResponse?: EvChargingStationResponse;
    environmentSensorResponse?: EnvironmentSensorResponse;
    videoBrowserResponse?: VideoBrowserResponse;
    quickStatsResponse?: QuickStatsResponse;
    essState?: ESSState;
    energyPricingSettingsRead?: EnergyPricingSettingsRead;
    userSettings?: UserSettings;
}
