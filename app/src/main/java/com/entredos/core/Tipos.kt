package com.entredos.core

/** Los dos titulares del hogar. Los datos se guardan a nombre del miembro, no del teléfono. */
enum class Miembro {
    A, B;

    val otro: Miembro get() = if (this == A) B else A
}

/** De quién es una categoría. Decide cómo se reparte cada gasto. */
enum class Responsable { A, B, COMPARTIDA }

enum class TipoGasto { GASTO, TRANSFERENCIA }

enum class EstadoGasto { PENDIENTE, CONFIRMADO, IGNORADO }

enum class OrigenGasto { MANUAL, NOTIFICACION, IMPORTACION }

enum class ModoPeriodo { MANUAL, AUTOMATICO }

enum class Frecuencia { SEMANAL, QUINCENAL, MENSUAL, ANUAL }

enum class EstadoLote { ACTIVO, DESHECHO }
