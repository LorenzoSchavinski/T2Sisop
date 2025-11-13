package src;

// --- GERENTE DE MEMÓRIA (PAGINAÇÃO) ---
public class GerenteMemoria {
    private final int tamMem;      // em palavras
    private final int tamPg;       // palavras por página/frame
    private final boolean[] frameLivre; // frameLivre[f] = true se livre
    public static class FrameOwner { public int pid=-1, page=-1; }
    private final java.util.ArrayDeque<Integer> fifo = new java.util.ArrayDeque<>();
    private final FrameOwner[] owner;     // owner[frame] = {pid,page}


    public GerenteMemoria(int tamMem, int tamPg) {
        this.tamMem = tamMem;
        this.tamPg = tamPg;
        int nFrames = tamMem / tamPg;
        this.frameLivre = new boolean[nFrames];
        owner = new FrameOwner[nFrames];
        for (int i = 0; i < nFrames; i++) {
        owner[i] = new FrameOwner();
        frameLivre[i] = true;
    }
    }

    public int getTamPg() { return tamPg; }
    public int getNumFrames() { return frameLivre.length; }

    // nroPalavras = código+dados do programa (em palavras)
    // Retorna tabela de páginas (page->frame) ou null se não couber

    public synchronized void touch(int f) {
        if (f >= 0 && f < frameLivre.length && !frameLivre[f]) {
            fifo.addLast(f);
        }
    }

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

    public synchronized void requeue(int f) {
        fifo.remove(f);
        fifo.addLast(f);
    }


    public void desaloca(int[] tabelaPaginas) {
        if (tabelaPaginas == null) return;
        for (int f : tabelaPaginas) {
            if (f >= 0) freeFrame(f);
        }
    }

    private int achaFrameLivre() {
        for (int f = 0; f < frameLivre.length; f++) {
            if (frameLivre[f]) return f;
        }
        return -1;
    }


    public synchronized int allocFrame() {
    int f = achaFrameLivre();
    if (f >= 0) {
        frameLivre[f] = false;
        fifo.addLast(f);
        return f;
    }
    return -1;
}

public synchronized void freeFrame(int f) {
    if (f >= 0 && f < frameLivre.length && !frameLivre[f]) {
        frameLivre[f] = true;
        // opcional: remover da fifo se ainda estiver lá
        fifo.remove(f);
        owner[f].pid = -1; owner[f].page = -1;
    }
}

public synchronized void setOwner(int frame, int pid, int page) {
    owner[frame].pid = pid;
    owner[frame].page = page;
}

public synchronized FrameOwner getOwner(int frame) { return owner[frame]; }

public synchronized int pickVictimFIFO() {
    // pega um frame que não esteja livre
    while (!fifo.isEmpty()) {
        int f = fifo.pollFirst();
        if (!frameLivre[f]) return f;
    }
    return -1;
}


}


// --- FIM GERENTE DE MEMÓRIA ---
