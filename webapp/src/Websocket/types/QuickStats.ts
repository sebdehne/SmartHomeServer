export type QuickStatsResponse = {
    powerImportInWatts: number;
    powerExportInWatts: number;
    costEnergyImportedToday: number;
    costEnergyImportedCurrentMonth: number;
    energyImportedTodayWattHours: number;
    outsideTemperature: number;
    currentEnergyPrice: number;
    essSystemStatus: string;
    essBatteryPower: number;
    essBatterySoC: number;
}