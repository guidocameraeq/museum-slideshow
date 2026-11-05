import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Prepara las fotos de marcos para la app: fondo y hueco transparentes, recorte, medida del grosor de cada lado
 * y reduccion. Uso (desde la raiz del repo, con el JDK 17):
 *   java herramientas/marcos/PrepararMarcos.java "<carpeta con los JPG>" [ladoLargo=1000]
 * Escribe app/src/main/res/drawable-nodpi/marco_<id>.png e imprime los grosores ya escalados
 * (izquierda, arriba, derecha, abajo) que van en MarcosFoto (DibujanteMarco.kt).
 */
public class PrepararMarcos {
    // foto de origen -> id del marco en la app (con guion bajo)
    static final String[][] FOTOS = {
        {"madera.jpg", "madera"}, {"dorado.jpg", "dorado"},
        {"dorado-redondo.jpg", "dorado_redondo"}, {"negro-filete.jpg", "filete"},
    };
    static final int SAT_MAX = 34;    // un pixel de papel o sombra es casi gris: poca diferencia entre canales
    static final int SAT_MAX_HUECO = 58;   // el papel del hueco puede ser color crema
    static final int LUZ_MIN_FONDO = 150, LUZ_MIN_HUECO = 105;

    public static void main(String[] a) throws Exception {
        File origen = new File(a[0]);
        int largo = a.length > 1 ? Integer.parseInt(a[1]) : 1000;
        File salida = new File("app/src/main/res/drawable-nodpi");
        salida.mkdirs();
        for (String[] f : FOTOS) procesar(new File(origen, f[0]), f[1], largo, salida);
    }

    static boolean ok(int p, int luzMin) {
        int satMax = luzMin == LUZ_MIN_HUECO ? SAT_MAX_HUECO : SAT_MAX;
        int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
        int mx = Math.max(r, Math.max(g, b)), mn = Math.min(r, Math.min(g, b));
        return mx - mn <= satMax && (r + g + b) / 3 >= luzMin;
    }

    static int dist(int p, int q) {
        return Math.max(Math.abs(((p >> 16) & 255) - ((q >> 16) & 255)),
               Math.max(Math.abs(((p >> 8) & 255) - ((q >> 8) & 255)), Math.abs((p & 255) - (q & 255))));
    }

    /** Relleno por inundacion; marca en `m` lo alcanzado desde las semillas. */
    static void inundar(int[] px, int w, int h, boolean[] m, int[] semillas, int luz, int unused) {
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int s : semillas) if (!m[s] && ok(px[s], luz)) { m[s] = true; q.add(s); }
        while (!q.isEmpty()) {
            int i = q.poll(); int x = i % w, y = i / w;
            if (x > 0) tomar(px, m, q, i - 1, luz, 0);
            if (x < w - 1) tomar(px, m, q, i + 1, luz, 0);
            if (y > 0) tomar(px, m, q, i - w, luz, 0);
            if (y < h - 1) tomar(px, m, q, i + w, luz, 0);
        }
    }

    static void tomar(int[] px, boolean[] m, ArrayDeque<Integer> q, int j, int luz, int unused) {
        if (!m[j] && ok(px[j], luz)) { m[j] = true; q.add(j); }
    }

    static void procesar(File f, String id, int largo, File salida) throws Exception {
        BufferedImage src = ImageIO.read(f);
        int w = src.getWidth(), h = src.getHeight();
        int[] px = src.getRGB(0, 0, w, h, null, 0, w);
        // color del fondo: promedio de las 4 esquinas (bloques de 8x8)
        long r = 0, g = 0, b = 0; int n = 0;
        int[][] esq = {{0, 0}, {w - 8, 0}, {0, h - 8}, {w - 8, h - 8}};
        for (int[] e : esq) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            int p = px[(e[1] + y) * w + e[0] + x]; r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255; n++;
        }
        int fondo = 0xFF000000 | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
        // 1) fondo de afuera: desde todos los bordes
        boolean[] trans = new boolean[w * h];
        int[] bordes = new int[2 * (w + h)]; int k = 0;
        for (int x = 0; x < w; x++) { bordes[k++] = x; bordes[k++] = (h - 1) * w + x; }
        for (int y = 0; y < h; y++) { bordes[k++] = y * w; bordes[k++] = y * w + w - 1; }
        inundar(px, w, h, trans, bordes, LUZ_MIN_FONDO, 0);
        // 2) hueco: desde el centro, contra el color del centro
        boolean[] hueco = new boolean[w * h];
        int c = (h / 2) * w + w / 2;
        inundar(px, w, h, hueco, new int[]{c}, LUZ_MIN_HUECO, 0);
        for (int i = 0; i < w * h; i++) if (hueco[i]) trans[i] = true;
        // 3) recorte al rectangulo de lo opaco
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (!trans[y * w + x]) {
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
        }
        int cw = x1 - x0 + 1, ch = y1 - y0 + 1;
        boolean[] t = new boolean[cw * ch], hu = new boolean[cw * ch];
        int[] pc = new int[cw * ch];
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) {
            int s = (y + y0) * w + x + x0;
            t[y * cw + x] = trans[s]; hu[y * cw + x] = hueco[s]; pc[y * cw + x] = px[s];
        }
        // 4) borde suave: se come 1 px de lo opaco pegado a lo transparente (sin halo blanco) y se suaviza 3x3
        boolean[] t2 = new boolean[cw * ch];
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) {
            boolean v = t[y * cw + x];
            for (int dy = -1; dy <= 1 && !v; dy++) for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx >= 0 && yy >= 0 && xx < cw && yy < ch && t[yy * cw + xx]) { v = true; break; }
            }
            t2[y * cw + x] = v;
        }
        BufferedImage rgba = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) {
            int s = 0, cnt = 0;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx, yy = y + dy;
                boolean tt = (xx < 0 || yy < 0 || xx >= cw || yy >= ch) || t2[yy * cw + xx];
                if (tt) s++; cnt++;
            }
            int al = 255 - (255 * s / cnt);
            rgba.setRGB(x, y, (al << 24) | (pc[y * cw + x] & 0xFFFFFF));
        }
        // 5) grosores: distancia del borde del recorte al hueco, mediana en la franja central de cada lado
        int hx0 = cw, hy0 = ch, hx1 = -1, hy1 = -1;
        for (int y = 0; y < ch; y++) for (int x = 0; x < cw; x++) if (hu[y * cw + x]) {
            if (x < hx0) hx0 = x; if (x > hx1) hx1 = x; if (y < hy0) hy0 = y; if (y > hy1) hy1 = y;
        }
        int bw = hx1 - hx0, bh = hy1 - hy0;
        int ya = hy0 + bh / 4, yb = hy1 - bh / 4, xa = hx0 + bw / 4, xb = hx1 - bw / 4;
        int[] izq = new int[yb - ya + 1], der = new int[yb - ya + 1];
        for (int y = ya; y <= yb; y++) {
            int l = 0, rr = 0;
            for (int x = 0; x < cw; x++) if (hu[y * cw + x]) { l = x; break; }
            for (int x = cw - 1; x >= 0; x--) if (hu[y * cw + x]) { rr = cw - 1 - x; break; }
            izq[y - ya] = l; der[y - ya] = rr;
        }
        int[] arr = new int[xb - xa + 1], aba = new int[xb - xa + 1];
        for (int x = xa; x <= xb; x++) {
            int tp = 0, bt = 0;
            for (int y = 0; y < ch; y++) if (hu[y * cw + x]) { tp = y; break; }
            for (int y = ch - 1; y >= 0; y--) if (hu[y * cw + x]) { bt = ch - 1 - y; break; }
            arr[x - xa] = tp; aba[x - xa] = bt;
        }
        double[] gr = {mediana(izq), mediana(arr), mediana(der), mediana(aba)};
        // 6) reducir (por pasos, en alfa premultiplicado)
        double esc = Math.min(1.0, (double) largo / Math.max(cw, ch));
        int nw = (int) Math.round(cw * esc), nh = (int) Math.round(ch * esc);
        BufferedImage cur = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g0 = cur.createGraphics(); g0.drawImage(rgba, 0, 0, null); g0.dispose();
        while (cur.getWidth() / 2 >= nw && cur.getHeight() / 2 >= nh) cur = escalar(cur, cur.getWidth() / 2, cur.getHeight() / 2);
        cur = escalar(cur, nw, nh);
        BufferedImage fin = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g1 = fin.createGraphics(); g1.drawImage(cur, 0, 0, null); g1.dispose();
        File out = new File(salida, "marco_" + id + ".png");
        ImageIO.write(fin, "png", out);
        System.out.printf("%s: %dx%d (recorte %dx%d) grosores izq/arr/der/aba = %d, %d, %d, %d  peso %d KB%n", id, nw, nh, cw, ch,
            Math.round(gr[0] * esc), Math.round(gr[1] * esc), Math.round(gr[2] * esc), Math.round(gr[3] * esc), out.length() / 1024);
    }

    static BufferedImage escalar(BufferedImage s, int w, int h) {
        BufferedImage d = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = d.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(s, 0, 0, w, h, null);
        g.dispose();
        return d;
    }

    static double mediana(int[] v) { int[] c = v.clone(); Arrays.sort(c); return c[c.length / 2]; }
}
