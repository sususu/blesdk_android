package com.huawo.nt.sdkdemo.ui.main

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.ActivityMainBinding
import com.huawo.nt.sdkdemo.util.EdgeToEdgeHelper
import com.huawo.nt.sdkdemo.util.LocaleHelper

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        EdgeToEdgeHelper.enable(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeHelper.install(this, binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }
    }
}
