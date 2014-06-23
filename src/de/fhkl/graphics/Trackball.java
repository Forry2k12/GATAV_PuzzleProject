package de.fhkl.graphics;

public class Trackball {
    static final float TRACKBALLSIZE = 0.8f;

    static void vzero(float v[]) {
        v[0] = 0.0f;
        v[1] = 0.0f;
        v[2] = 0.0f;
    }

    static void vset(float v[], float x, float y, float z) {
        v[0] = x;
        v[1] = y;
        v[2] = z;
    }

    static void vsub(float src1[], float src2[], float dst[]) {
        dst[0] = src1[0] - src2[0];
        dst[1] = src1[1] - src2[1];
        dst[2] = src1[2] - src2[2];
    }

    static void vcopy(float v1[], float v2[]) {
        int i;
        for (i = 0; i < 3; i++)
            v2[i] = v1[i];
    }

    static void vcross(float v1[], float v2[], float cross[]) {
        float temp[];

        temp = new float[3];

        temp[0] = (v1[1] * v2[2]) - (v1[2] * v2[1]);
        temp[1] = (v1[2] * v2[0]) - (v1[0] * v2[2]);
        temp[2] = (v1[0] * v2[1]) - (v1[1] * v2[0]);
        vcopy(temp, cross);
    }

    static float vlength(float v[]) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    static void vscale(float v[], float div) {
        v[0] *= div;
        v[1] *= div;
        v[2] *= div;
    }

    static void vnormal(float v[]) {
        vscale(v, (float) (1.0 / vlength(v)));
    }

    static float vdot(float v1[], float v2[]) {
        return v1[0] * v2[0] + v1[1] * v2[1] + v1[2] * v2[2];
    }

    static void vadd(float src1[], float src2[], float dst[]) {
        dst[0] = src1[0] + src2[0];
        dst[1] = src1[1] + src2[1];
        dst[2] = src1[2] + src2[2];
    }

    public static void trackball(float q[], float p1x, float p1y,
            float p2x, float p2y) {
        float a[]; 
        float phi; 
        float p1[], p2[], d[];
        float t;

        a = new float[3];
        p1 = new float[3];
        p2 = new float[3];
        d = new float[3];
        if (p1x == p2x && p1y == p2y) {
            vzero(q);
            q[3] = 1.0f;
            return;
        }

        vset(p1, p1x, p1y, tb_project_to_sphere(TRACKBALLSIZE, p1x, p1y));
        vset(p2, p2x, p2y, tb_project_to_sphere(TRACKBALLSIZE, p2x, p2y));

        vcross(p2, p1, a);

        vsub(p1, p2, d);
        t = (float) (vlength(d) / (2.0 * TRACKBALLSIZE));

        if (t > 1.0)
            t = 1.0f;
        if (t < -1.0)
            t = -1.0f;
        phi = (float) (2.0 * Math.asin(t));

        axis_to_quat(a, phi, q);
    }

    public static void axis_to_quat(float a[], float phi, float q[]) {
        vnormal(a);
        vcopy(a, q);
        vscale(q, (float) Math.sin(phi / 2.0));
        q[3] = (float) Math.cos(phi / 2.0);
    }

    static float tb_project_to_sphere(float r, float x, float y) {
        float d, t, z;

        d = (float) Math.sqrt(x * x + y * y);
        if (d < r * 0.70710678118654752440) { /* Inside sphere */
            z = (float) Math.sqrt(r * r - d * d);
        } else { /* On hyperbola */
            t = (float) (r / 1.41421356237309504880);
            z = t * t / d;
        }
        return z;
    }

    public static final int RENORMCOUNT = 97;
    public static int count = 0;

    public static void add_quats(float q1[], float q2[], float dest[]) {
        float t1[], t2[], t3[];
        float tf[];

        t1 = new float[4];
        t2 = new float[4];
        t3 = new float[4];
        tf = new float[4];

        vcopy(q1, t1);
        vscale(t1, q2[3]);

        vcopy(q2, t2);
        vscale(t2, q1[3]);

        vcross(q2, q1, t3);
        vadd(t1, t2, tf);
        vadd(t3, tf, tf);
        tf[3] = q1[3] * q2[3] - vdot(q1, q2);

        dest[0] = tf[0];
        dest[1] = tf[1];
        dest[2] = tf[2];
        dest[3] = tf[3];

        if (++count > RENORMCOUNT) {
            count = 0;
            normalize_quat(dest);
        }
    }

    static void normalize_quat(float q[]) {
        int i;
        float mag;

        mag = (q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        for (i = 0; i < 4; i++)
            q[i] /= mag;
    }

    public static void build_rotmatrix(float m[], float q[]) {

        m[0] = (float) (1.0 - 2.0 * (q[1] * q[1] + q[2] * q[2]));
        m[1] = (float) (2.0 * (q[0] * q[1] - q[2] * q[3]));
        m[2] = (float) (2.0 * (q[2] * q[0] + q[1] * q[3]));
        m[3] = 0.0f;

        m[4] = (float) (2.0 * (q[0] * q[1] + q[2] * q[3]));
        m[5] = (float) (1.0 - 2.0 * (q[2] * q[2] + q[0] * q[0]));
        m[6] = (float) (2.0 * (q[1] * q[2] - q[0] * q[3]));
        m[7] = 0.0f;

        m[8] = (float) (2.0 * (q[2] * q[0] - q[1] * q[3]));
        m[9] = (float) (2.0 * (q[1] * q[2] + q[0] * q[3]));
        m[10] = (float) (1.0 - 2.0 * (q[1] * q[1] + q[0] * q[0]));
        m[11] = 0.0f;

        m[12] = 0.0f;
        m[13] = 0.0f;
        m[14] = 0.0f;
        m[15] = 1.0f;
    }
}
