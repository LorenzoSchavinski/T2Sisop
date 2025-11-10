package src;

// --- GERENTE DE MEMÓRIA (PAGINAÇÃO) ---
public class GerenteMemoria {
    private final int tamMem;      // em palavras
    private final int tamPg;       // palavras por página/frame
    private final boolean[] frameLivre; // frameLivre[f] = true se livre

    public GerenteMemoria(int tamMem, int tamPg) {
        this.tamMem = tamMem;
        this.tamPg = tamPg;
        int nFrames = tamMem / tamPg;
        this.frameLivre = new boolean[nFrames];
        for (int i = 0; i < nFrames; i++) frameLivre[i] = true;
    }

    public int getTamPg() { return tamPg; }
    public int getNumFrames() { return frameLivre.length; }

    // nroPalavras = código+dados do programa (em palavras)
    // Retorna tabela de páginas (page->frame) ou null se não couber
    public int[] aloca(int nroPalavras) {
        int nPaginas = (nroPalavras + tamPg - 1) / tamPg;
        int[] tabela = new int[nPaginas];
        // selecionar frames livres
        for (int p = 0; p < nPaginas; p++) {
            int f = achaFrameLivre();
            if (f < 0) { // não coube: desfaz e falha
                for (int i = 0; i < p; i++) frameLivre[tabela[i]] = true;
                return null;
            }
            frameLivre[f] = false;
            tabela[p] = f;
        }
        return tabela;
    }

    public void desaloca(int[] tabelaPaginas) {
        if (tabelaPaginas == null) return;
        for (int f : tabelaPaginas) {
            if (f >= 0 && f < frameLivre.length) frameLivre[f] = true;
        }
    }

    private int achaFrameLivre() {
        for (int f = 0; f < frameLivre.length; f++) {
            if (frameLivre[f]) return f;
        }
        return -1;
    }
}
// --- FIM GERENTE DE MEMÓRIA ---
