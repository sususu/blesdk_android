package com.huawo.nt.sdkdemo.ui.scan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.data.model.BleDevice
import com.huawo.nt.sdkdemo.databinding.ItemDeviceBinding

class DeviceListAdapter(
    private val onConnect: (BleDevice) -> Unit,
) : RecyclerView.Adapter<DeviceListAdapter.VH>() {
    private val items = mutableListOf<BleDevice>()
    private var connecting = false

    fun submit(devices: List<BleDevice>, connecting: Boolean) {
        items.clear()
        items.addAll(devices)
        this.connecting = connecting
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], connecting, onConnect)
    }

    override fun getItemCount(): Int = items.size

    class VH(private val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: BleDevice, connecting: Boolean, onConnect: (BleDevice) -> Unit) {
            val name =
                if (device.name.isNullOrBlank()) {
                    binding.root.context.getString(R.string.unknown_device)
                } else {
                    device.name
                }
            binding.deviceName.text = name
            binding.deviceMeta.text = "${device.macAddress}  ·  RSSI ${device.rssi ?: "-"}"
            binding.btnConnect.isEnabled = !connecting
            binding.btnConnect.setOnClickListener { onConnect(device) }
            binding.root.setOnClickListener { if (!connecting) onConnect(device) }
        }
    }
}
