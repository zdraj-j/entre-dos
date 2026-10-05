package com.entredos.di

import com.entredos.core.sync.MotorSync
import com.entredos.core.sync.RelojHlc
import com.entredos.data.sync.CuentaHogar
import com.entredos.data.sync.DriveAlmacen
import com.entredos.data.sync.JsonCodec
import com.entredos.data.sync.ProveedorToken
import com.entredos.data.sync.RoomBaseLocal
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindings {
    @Binds abstract fun proveedorToken(cuenta: CuentaHogar): ProveedorToken
}

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides @Singleton
    fun motor(drive: DriveAlmacen, base: RoomBaseLocal, codec: JsonCodec, reloj: RelojHlc): MotorSync =
        MotorSync(drive, base, codec, reloj)
}
