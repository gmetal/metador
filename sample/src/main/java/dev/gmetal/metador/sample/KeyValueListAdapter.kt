package dev.gmetal.metador.sample

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import dev.gmetal.metador.sample.databinding.ItemKeyValueBinding

class KeyValueListAdapter(private val inflater: LayoutInflater) :
    ListAdapter<Pair<String, String>, KeyValueListAdapter.KeyValueViewHolder>(
        object : DiffUtil.ItemCallback<Pair<String, String>>() {
            override fun areItemsTheSame(
                oldItem: Pair<String, String>,
                newItem: Pair<String, String>
            ): Boolean = (oldItem.first == newItem.first) && (oldItem.second == oldItem.second)

            override fun areContentsTheSame(
                oldItem: Pair<String, String>,
                newItem: Pair<String, String>
            ): Boolean = (oldItem.first == newItem.first) && (oldItem.second == oldItem.second)
        }
    ) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KeyValueViewHolder =
        KeyValueViewHolder(ItemKeyValueBinding.inflate(inflater, parent, false))

    override fun onBindViewHolder(holder: KeyValueViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class KeyValueViewHolder(binding: ItemKeyValueBinding) :
        GenericViewHolder<ItemKeyValueBinding, Pair<String, String>>(binding) {

        override fun bind(value: Pair<String, String>) {
            binding.key.text = value.first
            binding.value.text = value.second
        }
    }
}

abstract class GenericViewHolder<T : ViewBinding, V>(val binding: T) :
    RecyclerView.ViewHolder(binding.root) {
    abstract fun bind(value: V)
}
