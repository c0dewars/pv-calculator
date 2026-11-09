package io.github.c0dewars.pvcalculator.data

import kotlinx.serialization.Serializable

/** Everything the app stores. Persisted as a single JSON document on the device. */
@Serializable
data class AppData(
    val version: Int = 1,
    val system: SolarSystem = SolarSystem(),
    val tariffs: Tariffs = Tariffs(),
    /** ISO 4217 code. Null means "use the currency of the device locale". */
    val currencyCode: String? = null,
    val bills: List<Bill> = emptyList(),
    val production: List<ProductionEntry> = emptyList(),
    /** Link to a DeyeCloud station (secrets such as the app secret and token are stored separately). */
    val deye: DeyeLink? = null,
)

@Serializable
data class DeyeLink(
    /** Name of [io.github.c0dewars.pvcalculator.domain.DeyeDataCenter]. */
    val dataCenter: String,
    val stationId: Long,
    val stationName: String? = null,
    /** ISO date-time of the last successful sync. */
    val lastSync: String? = null,
    /** Last day (ISO date) included in a sync. */
    val lastSyncedDay: String? = null,
)

@Serializable
data class SolarSystem(
    /** Total amount paid for the installation (panels, inverter, battery, labour...). */
    val investment: Double = 0.0,
    /** ISO date (yyyy-MM-dd) the system started producing. */
    val installDate: String? = null,
    val capacityKwp: Double? = null,
    /** Optional estimate (e.g. from the installer's quote) used while there is less than a year of data. */
    val expectedAnnualProductionKwh: Double? = null,
    /** Later additions such as a battery, more panels or a new inverter. */
    val extraInvestments: List<ExtraInvestment> = emptyList(),
) {
    val totalInvestment: Double get() = investment + extraInvestments.sumOf { it.amount }
}

@Serializable
data class ExtraInvestment(
    val id: String,
    val description: String,
    val amount: Double,
    /** ISO date (yyyy-MM-dd). */
    val date: String? = null,
)

@Serializable
data class Tariffs(
    /** Price paid per kWh bought from the grid, used for months without a bill. */
    val gridPricePerKwh: Double = 0.20,
    /** Price received per kWh exported to the grid (0 if you don't sell surplus energy). */
    val feedInPricePerKwh: Double = 0.05,
    /** Share of the solar production used directly at home, used when a bill does not say how much was exported. */
    val selfConsumptionPercent: Double = 70.0,
    /** Expected yearly increase of the electricity price, for the payback projection. */
    val annualPriceIncreasePercent: Double = 3.0,
    /** Expected yearly loss of panel efficiency. */
    val panelDegradationPercent: Double = 0.5,
)

/** A file (PDF or image) stored inside the app's private storage. */
@Serializable
data class Attachment(
    /** File name inside the app's attachments folder. */
    val fileName: String,
    val mimeType: String,
    /** Name of the file as the user picked it. */
    val originalName: String? = null,
) {
    val isPdf: Boolean get() = mimeType == "application/pdf"
}

@Serializable
data class Bill(
    val id: String,
    /** Year and month the bill refers to (yyyy-MM). */
    val month: String,
    /** Energy bought from the grid in the billing period. */
    val importedKwh: Double,
    /** Total amount paid. */
    val amount: Double,
    /** Fixed part of the bill (standing / power charge) that solar panels do not reduce. */
    val fixedCharges: Double? = null,
    /** Energy exported to the grid, when the bill or the meter reports it. */
    val exportedKwh: Double? = null,
    val attachment: Attachment? = null,
)

@Serializable
data class ProductionEntry(
    val id: String,
    /** ISO date (yyyy-MM-dd). For whole-month entries, the first day of the month. */
    val date: String,
    val kwh: Double,
    /** True when [kwh] is the total of the whole month instead of a single day. */
    val wholeMonth: Boolean = false,
    /** Energy sent to the grid in the same period, when the monitoring app reports it. */
    val exportedKwh: Double? = null,
    val attachment: Attachment? = null,
    /** Where the value came from: null for manual entries, "deye" for DeyeCloud sync. */
    val source: String? = null,
)
