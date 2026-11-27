package id.vanderleinz.lumo;

enum PetStyle {
    CLASSIC("classic", "Andi Klasik", R.drawable.andi_body_klasik, 17f, 56f, 0xffffe5c7, 0xff8e6b72),
    MOMO("momo", "Momo · Persik", R.drawable.andi_body_momo, 13f, 58f, 0xff623a40, 0xffe27f79),
    PIKO("piko", "Piko · Awan", R.drawable.andi_body_piko, 13f, 61f, 0xff3d537a, 0xffeca8bc),
    LUMI("lumi", "Lumi · Bintang", R.drawable.andi_body_lumi, 11f, 58f, 0xff6d463c, 0xffec9c78);

    final String id;
    final String label;
    final int body;
    final float eyeSpacing;
    final float eyeY;

    final int faceColor;
    final int cheekColor;

    PetStyle(String id, String label, int body, float eyeSpacing, float eyeY,
             int faceColor, int cheekColor) {
        this.id = id;
        this.label = label;
        this.body = body;
        this.eyeSpacing = eyeSpacing;
        this.eyeY = eyeY;
        this.faceColor = faceColor;
        this.cheekColor = cheekColor;
    }

    static PetStyle fromId(String id) {
        for (PetStyle style : values()) {
            if (style.id.equals(id)) return style;
        }
        return CLASSIC;
    }
}
