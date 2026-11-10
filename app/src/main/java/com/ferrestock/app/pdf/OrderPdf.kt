package com.ferrestock.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.ferrestock.app.R
import com.ferrestock.app.data.StockOrder
import com.ferrestock.app.data.formatDateTime
import java.io.File

/** Dibuja la orden en un PDF A4 con la paleta y la tipografía de la app. */
object OrderPdf {
    private const val W = 595
    private const val H = 842
    private const val M = 40f            // margen
    private const val ROW = 28f          // alto de fila de la tabla
    private const val BOTTOM = H - 64f   // límite inferior del contenido

    private const val NAVY = 0xFF001A6E.toInt()
    private const val BLUE = 0xFF074799.toInt()
    private const val TEAL = 0xFF009990.toInt()
    private const val LIME = 0xFFE1FFBB.toInt()
    private const val MUTED = 0xFF5A678F.toInt()
    private const val ZEBRA = 0xFFF3F7FB.toInt()
    private const val LINE = 0xFFD5DCEC.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    private class Fonts(context: Context) {
        private val file = File(context.cacheDir, "outfit.ttf").also { f ->
            if (!f.exists() || f.length() == 0L) {
                context.resources.openRawResource(R.font.outfit).use { input ->
                    f.outputStream().use { input.copyTo(it) }
                }
            }
        }

        private fun weight(w: Int): Typeface = runCatching {
            Typeface.Builder(file).setFontVariationSettings("'wght' $w").build()
        }.getOrDefault(Typeface.DEFAULT)

        val regular = weight(400)
        val medium = weight(500)
        val bold = weight(700)
    }

    private fun text(face: Typeface, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = size
            this.color = color
            textAlign = align
        }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun fit(s: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(s, paint, width, TextUtils.TruncateAt.END).toString()

    fun create(context: Context, order: StockOrder): File {
        val fonts = Fonts(context)
        val doc = PdfDocument()
        var pageNumber = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        val colQty = W - M - 14f           // borde derecho de la columna "Cantidad"
        val colName = M + 46f              // inicio de la columna "Producto"
        val nameWidth = colQty - 110f - colName

        fun footer() {
            c.drawRect(M, H - 46f, W - M, H - 45f, fill(LINE))
            c.drawText("Ferrestock · ${order.code}", M, H - 28f, text(fonts.regular, 9f, MUTED))
            c.drawText("Página $pageNumber", W - M, H - 28f, text(fonts.regular, 9f, MUTED, Paint.Align.RIGHT))
        }

        fun tableHeader() {
            c.drawRoundRect(RectF(M, y, W - M, y + ROW), 10f, 10f, fill(BLUE))
            val p = text(fonts.bold, 10f, WHITE)
            c.drawText("#", M + 16f, y + 18f, p)
            c.drawText("PRODUCTO", colName, y + 18f, p)
            c.drawText("CANTIDAD", colQty, y + 18f, text(fonts.bold, 10f, WHITE, Paint.Align.RIGHT))
            y += ROW + 4f
        }

        fun newPage() {
            if (pageNumber > 0) {
                footer()
                doc.finishPage(page)
            }
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNumber).create())
            c = page.canvas
            y = M
        }

        // ---------- Página 1: encabezado ----------
        newPage()
        val headerH = 168f
        c.drawRect(0f, 0f, W.toFloat(), headerH, fill(NAVY))
        c.save()
        c.clipRect(0f, 0f, W.toFloat(), headerH)
        c.drawCircle(W - 50f, 18f, 96f, fill(BLUE))
        c.drawCircle(W - 150f, 128f, 26f, fill(TEAL))
        c.restore()

        c.drawText("FERRESTOCK", M, 52f, text(fonts.bold, 11f, LIME).apply { letterSpacing = 0.18f })
        c.drawText("Orden de retiro", M, 88f, text(fonts.bold, 30f, WHITE))

        val codePaint = text(fonts.bold, 13f, NAVY)
        val codeW = codePaint.measureText(order.code) + 28f
        c.drawRoundRect(RectF(M, 104f, M + codeW, 132f), 14f, 14f, fill(LIME))
        c.drawText(order.code, M + 14f, 123f, codePaint)

        // Tarjeta de datos, montada sobre el borde del encabezado
        val cardTop = headerH - 22f
        val cardH = 64f
        c.drawRoundRect(RectF(M, cardTop, W - M, cardTop + cardH), 18f, 18f, fill(WHITE))
        c.drawRoundRect(
            RectF(M, cardTop, W - M, cardTop + cardH), 18f, 18f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = LINE },
        )
        val label = text(fonts.medium, 9f, TEAL)
        val value = text(fonts.bold, 12f, NAVY)
        val third = (W - 2 * M) / 3f
        listOf(
            "RESPONSABLE" to order.userName,
            "FECHA" to formatDateTime(order.createdAt),
            "TOTAL" to "${order.items.size} ${if (order.items.size == 1) "producto" else "productos"} · ${order.totalUnits} u",
        ).forEachIndexed { i, (k, v) ->
            val x = M + 18f + third * i
            c.drawText(k, x, cardTop + 26f, label)
            c.drawText(fit(v, value, third - 26f), x, cardTop + 45f, value)
        }
        y = cardTop + cardH + 26f

        // ---------- Descripción ----------
        if (order.description.isNotBlank()) {
            c.drawText("DESCRIPCIÓN", M, y, text(fonts.bold, 9f, TEAL).apply { letterSpacing = 0.12f })
            y += 8f
            val body = text(fonts.regular, 12f, NAVY)
            val layout = StaticLayout.Builder
                .obtain(order.description, 0, order.description.length, body, (W - 2 * M).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1f)
                .setMaxLines(8)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            c.save()
            c.translate(M, y)
            layout.draw(c)
            c.restore()
            y += layout.height + 22f
        }

        // ---------- Tabla ----------
        tableHeader()
        val idx = text(fonts.medium, 11f, MUTED)
        val name = text(fonts.medium, 12f, NAVY)
        val qty = text(fonts.bold, 12f, NAVY, Paint.Align.RIGHT)
        order.items.forEachIndexed { i, item ->
            if (y + ROW > BOTTOM) {
                newPage()
                tableHeader()
            }
            if (i % 2 == 1) c.drawRoundRect(RectF(M, y, W - M, y + ROW), 8f, 8f, fill(ZEBRA))
            c.drawText((i + 1).toString(), M + 16f, y + 18f, idx)
            c.drawText(fit(item.productName, name, nameWidth), colName, y + 18f, name)
            c.drawText("${item.quantity} ${item.unit}", colQty, y + 18f, qty)
            y += ROW
        }

        // ---------- Total ----------
        if (y + 150f > BOTTOM) newPage()
        y += 10f
        c.drawRoundRect(RectF(W - M - 230f, y, W - M, y + 36f), 12f, 12f, fill(LIME))
        c.drawText("Total de unidades", W - M - 214f, y + 23f, text(fonts.medium, 11f, BLUE))
        c.drawText(order.totalUnits.toString(), colQty, y + 24f, text(fonts.bold, 15f, NAVY, Paint.Align.RIGHT))
        y += 36f

        // ---------- Firmas ----------
        val signY = (y + 84f).coerceAtMost(BOTTOM - 20f)
        val signW = 190f
        val small = text(fonts.regular, 9f, MUTED, Paint.Align.CENTER)
        listOf(M to "Entregó", W - M - signW to "Recibió").forEach { (x, caption) ->
            c.drawRect(x, signY, x + signW, signY + 1f, fill(NAVY))
            c.drawText(caption, x + signW / 2, signY + 14f, small)
        }

        footer()
        doc.finishPage(page)

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "${order.code}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
