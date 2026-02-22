package com.example.SafeLYN

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ItemFeatureCardGuardianActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: GuardianAdapter
    private val guardianList = mutableListOf<GuardianModel>()
    private lateinit var prefsManager: PrefsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guardian)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        prefsManager = PrefsManager(this) // Wired to V2 Storage

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewGuardians)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Explicitly specifying GuardianModel type to resolve inference errors
        adapter = GuardianAdapter(guardianList) { guardian: GuardianModel ->
            deleteGuardian(guardian)
        }
        recyclerView.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener { showAddGuardianDialog() }
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        loadGuardians()
    }

    private fun loadGuardians() {
        guardianList.clear()

        // Fetching fresh data from the V2 Storage bridge
        val names = prefsManager.getGuardianNamesList()
        val numbers = prefsManager.getGuardianNumbers()

        for (i in names.indices) {
            if (i < numbers.size) {
                guardianList.add(GuardianModel(names[i], numbers[i], ""))
            }
        }

        adapter.notifyDataSetChanged()
        findViewById<TextView>(R.id.tvNoGuardians).visibility =
            if (guardianList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun saveGuardian(name: String, phone: String, email: String) {
        // Save current list plus the new entry to V2 Storage
        val currentNames = prefsManager.getGuardianNamesList().toMutableList()
        val currentNumbers = prefsManager.getGuardianNumbers().toMutableList()

        currentNames.add(name)
        currentNumbers.add(phone)

        prefsManager.saveGuardianList(currentNames, currentNumbers)

        // Firebase Backup logic
        val userId = auth.currentUser?.uid
        if (userId != null) {
            val guardian = hashMapOf("name" to name, "phone" to phone, "email" to email)
            firestore.collection("users").document(userId).collection("guardians").add(guardian)
        }

        loadGuardians()
        Toast.makeText(this, "Guardian Added to V2 Storage", Toast.LENGTH_SHORT).show()
    }

    private fun deleteGuardian(guardian: GuardianModel) {
        guardianList.remove(guardian)

        // Synchronize deletion back to V2 Storage
        val newNames = guardianList.map { it.name }
        val newNumbers = guardianList.map { it.phone }
        prefsManager.saveGuardianList(newNames, newNumbers)

        loadGuardians()
        Toast.makeText(this, "Guardian Removed", Toast.LENGTH_SHORT).show()
    }

    // Dialog and Contact Picker Logic
    private fun showAddGuardianDialog(prefillName: String = "", prefillPhone: String = "") {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Guardian")
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_guardian, null)
        builder.setView(dialogView)

        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etPhone = dialogView.findViewById<EditText>(R.id.etPhone)
        val etEmail = dialogView.findViewById<EditText>(R.id.etEmail)

        if (prefillName.isNotEmpty()) etName.setText(prefillName)
        if (prefillPhone.isNotEmpty()) etPhone.setText(prefillPhone)

        builder.setPositiveButton("Save") { _, _ ->
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val email = etEmail.text.toString().trim()
            if (name.isNotEmpty() && phone.isNotEmpty()) saveGuardian(name, phone, email)
        }
        builder.setNegativeButton("Cancel") { d, _ -> d.cancel() }
        builder.setNeutralButton("Pick Contact") { _, _ ->
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 101)
            } else {
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                startActivityForResult(intent, 1)
            }
        }
        builder.show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            val contactUri: Uri? = data?.data
            contactUri?.let {
                val cursor: Cursor? = contentResolver.query(it, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val numIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        showAddGuardianDialog(c.getString(nameIndex) ?: "Unknown", c.getString(numIndex) ?: "")
                    }
                }
            }
        }
    }
}

// --- DATA MODEL (Included here to resolve Unresolved Reference) ---
data class GuardianModel(val name: String, val phone: String, val email: String)

// --- ADAPTER (Included here to resolve Unresolved Reference) ---
class GuardianAdapter(
    private val list: List<GuardianModel>,
    private val onDeleteClick: (GuardianModel) -> Unit
) : RecyclerView.Adapter<GuardianAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.txtName)
        val phone: TextView = view.findViewById(R.id.txtPhone)
        val email: TextView = view.findViewById(R.id.txtEmail)
        val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_guardian, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.name.text = item.name
        holder.phone.text = item.phone
        holder.email.text = item.email
        holder.email.visibility = if (item.email.isNotEmpty()) View.VISIBLE else View.GONE
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount() = list.size
}