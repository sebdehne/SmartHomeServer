export type UserRole =
    "garageDoor"
    | "evCharging"
    | "energyStorageSystem"
    | "energyPricing"
    | "heaterUnderFloor"
    | "environmentSensors"
    | "cameras"
    | "recordings"
    | "firmwareUpgrades"
    ;

export type Level = "none" | "read" | "readWrite";

export type UserSettings = {
    authorization: AuthorizationMap;
}

export type AuthorizationMap = {
    [key in UserRole]: Level;
}
