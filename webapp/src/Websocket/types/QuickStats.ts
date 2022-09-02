

export class QuickStatsResponse {
    public powerImportInWatts: number;
    public costEnergyImportedToday: number;
    public costEnergyImportedCurrentMonth: number;
    public energyImportedTodayWattHours: number;
    public outsideTemperature: number;
    public currentEnergyPrice: number;


    constructor(powerImportInWatts: number, costEnergyImportedToday: number, costEnergyImportedCurrentMonth: number, energyImportedTodayWattHours: number, outsideTemperature: number, currentEnergyPrice: number) {
        this.powerImportInWatts = powerImportInWatts;
        this.costEnergyImportedToday = costEnergyImportedToday;
        this.costEnergyImportedCurrentMonth = costEnergyImportedCurrentMonth;
        this.energyImportedTodayWattHours = energyImportedTodayWattHours;
        this.outsideTemperature = outsideTemperature;
        this.currentEnergyPrice = currentEnergyPrice;
    }
}