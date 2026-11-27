package id.vanderleinz.lumo;

enum PetStyle {
    CLASSIC("classic", "Pari Klasik", R.drawable.andi_body_klasik, 12f, 56f),
    ORIGAMI("origami", "Pari Origami", R.drawable.andi_body_origami, 12f, 55f),
    MOCHI("mochi", "Pari Mochi", R.drawable.andi_body_mochi, 14f, 58f),
    RETRO("retro", "Pari Retro", R.drawable.andi_body_retro, 13f, 58f);

    final String id;
    final String label;
    final int body;
    final float eyeSpacing;
    final float eyeY;

    PetStyle(String id, String label, int body, float eyeSpacing, float eyeY) {
        this.id = id;
        this.label = label;
        this.body = body;
        this.eyeSpacing = eyeSpacing;
        this.eyeY = eyeY;
    }

    static PetStyle fromId(String id) {
        for (PetStyle style : values()) {
            if (style.id.equals(id)) return style;
        }
        return CLASSIC;
    }
}
