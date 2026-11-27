package com.example.auditoriafiscal.model

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface

/**
 * Utilitário de imagem usado pelo Repository (OCR) e pela View (miniatura da evidência).
 * Fica fora do Repository para a View não depender da camada de dados.
 */
object ImagemUtils {

    /**
     * Decodifica a imagem já reduzida (maior lado >= ladoMaximo) e corrige a rotação EXIF.
     * Deve ser chamada fora da thread principal.
     */
    fun carregarBitmap(caminho: String, ladoMaximo: Int): Bitmap? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(caminho, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

        var amostra = 1
        val maiorLado = maxOf(limites.outWidth, limites.outHeight)
        while (maiorLado / (amostra * 2) >= ladoMaximo) amostra *= 2

        val opcoes = BitmapFactory.Options().apply { inSampleSize = amostra }
        val bitmap = BitmapFactory.decodeFile(caminho, opcoes) ?: return null

        val graus = try {
            when (ExifInterface(caminho).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) {
            0f
        }
        if (graus == 0f) return bitmap

        val matriz = Matrix().apply { postRotate(graus) }
        val girado = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
        if (girado !== bitmap) bitmap.recycle()
        return girado
    }
}
