private suspend fun loadHealthData() {
    binding.tvStatus.text = "Loading…"
    binding.progressBar.visibility = View.VISIBLE

    try {
        val data = healthManager.getLatestHealthData()

        binding.heartRateChart.heartRates = data.heartRates
        binding.sleepChart.sleepStages    = data.sleepStages
        binding.tvSleepDuration.text = data.sleepDurationMinutes?.let {
            val h = it / 60; val m = it % 60; "${h}h ${m}m total"
        } ?: "No sleep data"

        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        binding.tvStatus.text = "Last synced: ${fmt.format(Date())}"

        // Pass the data we already have — no second Health Connect read
        pushDataToWidgets(data)

    } catch (e: Exception) {
        binding.tvStatus.text = "Read error: ${e.localizedMessage}"
    } finally {
        binding.progressBar.visibility = View.GONE
    }
}

private fun pushDataToWidgets(data: HealthData) {
    val manager = AppWidgetManager.getInstance(this)
    val ids = manager.getAppWidgetIds(ComponentName(this, HealthAppWidget::class.java))
    ids.forEach { id ->
        HealthAppWidget.updateWidgetWithData(this, manager, id, data)
    }
}
