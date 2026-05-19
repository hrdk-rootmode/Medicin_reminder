package com.example.medicinreminder.data.repository

import android.content.Context
import com.example.medicinreminder.billing.BillingManager
import com.example.medicinreminder.data.database.AppDatabase
import com.example.medicinreminder.data.dao.MedicineDao
import com.example.medicinreminder.data.dao.ReminderScheduleDao
import com.example.medicinreminder.data.dao.DoseLogDao
import com.example.medicinreminder.data.dao.UserEntitlementDao
import com.example.medicinreminder.data.dao.RemoteMedicineDao
import com.example.medicinreminder.data.repository.LocalMedicineInfoRepository
import com.example.medicinreminder.data.repository.MedicineInfoRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.getDatabase(context)
    
    val medicineDao: MedicineDao = database.medicineDao()
    val reminderScheduleDao: ReminderScheduleDao = database.reminderScheduleDao()
    val doseLogDao: DoseLogDao = database.doseLogDao()
    val userEntitlementDao: UserEntitlementDao = database.userEntitlementDao()
    val remoteMedicineDao: RemoteMedicineDao = database.remoteMedicineDao()
    
    val medicineRepository: MedicineRepository = MedicineRepository(medicineDao)
    val reminderRepository: ReminderRepository = ReminderRepository(reminderScheduleDao, doseLogDao)
    val entitlementRepository: EntitlementRepository = EntitlementRepository(userEntitlementDao)
    val remoteMedicineRepository: RemoteMedicineRepository = RemoteMedicineRepository(remoteMedicineDao)
    val medicineInfoRepository: MedicineInfoRepository = LocalMedicineInfoRepository(appContext, remoteMedicineRepository)
    
    val billingManager: BillingManager = BillingManager(context, entitlementRepository)
}
