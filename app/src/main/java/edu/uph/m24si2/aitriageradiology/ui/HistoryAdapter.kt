package edu.uph.m24si2.aitriageradiology.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import edu.uph.m24si2.aitriageradiology.R
import edu.uph.m24si2.aitriageradiology.data.model.PatientScanRecord
import java.util.Locale

class HistoryAdapter(
    private val context: Context,
    private var records: MutableList<PatientScanRecord> = mutableListOf(),
    private val onItemClick: ((PatientScanRecord) -> Unit)? = null
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHistoryPatientId: TextView = view.findViewById(R.id.tvHistoryPatientId)
        val tvHistoryBadge: TextView = view.findViewById(R.id.tvHistoryBadge)
        val tvHistoryPatientDetails: TextView = view.findViewById(R.id.tvHistoryPatientDetails)
        val tvHistoryFinding: TextView = view.findViewById(R.id.tvHistoryFinding)
        val tvHistoryConfidence: TextView = view.findViewById(R.id.tvHistoryConfidence)
        val tvHistoryTimestamp: TextView = view.findViewById(R.id.tvHistoryTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_triage_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]

        holder.tvHistoryPatientId.text = record.patientId
        holder.tvHistoryPatientDetails.text = context.getString(
            R.string.sample_chest_xray
        ).let {
            "Patient: ${record.patientName} | ${record.age} yrs (${record.gender}) - ${record.modality}"
        }

        holder.tvHistoryFinding.text = "Finding: ${record.primaryFinding}"
        val confPercent = (record.confidence * 100).let { String.format(Locale.getDefault(), "%.1f%%", it) }
        holder.tvHistoryConfidence.text = "Confidence: $confPercent"
        holder.tvHistoryTimestamp.text = record.timestamp

        // Set badge style depending on Triage Category
        val categoryUpper = record.triageCategory.uppercase(Locale.getDefault())
        when {
            categoryUpper.contains("CRITICAL") -> {
                holder.tvHistoryBadge.text = "CRITICAL"
                holder.tvHistoryBadge.setBackgroundResource(R.drawable.bg_badge_critical)
            }
            categoryUpper.contains("URGENT") -> {
                holder.tvHistoryBadge.text = "URGENT"
                holder.tvHistoryBadge.setBackgroundResource(R.drawable.bg_badge_urgent)
            }
            else -> {
                holder.tvHistoryBadge.text = "NORMAL"
                holder.tvHistoryBadge.setBackgroundResource(R.drawable.bg_badge_normal)
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick?.invoke(record)
        }
    }

    override fun getItemCount(): Int = records.size

    fun updateRecords(newRecords: List<PatientScanRecord>) {
        records.clear()
        records.addAll(newRecords)
        notifyDataSetChanged()
    }

    fun addRecord(record: PatientScanRecord) {
        records.add(0, record)
        notifyItemInserted(0)
    }

    fun clearAll() {
        records.clear()
        notifyDataSetChanged()
    }
}
