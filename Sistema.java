package src;
// PUCRS - Escola Politécnica - Sistemas Operacionais
// Prof. Fernando Dotti
// Código fornecido como parte da solução do projeto de Sistemas Operacionais
//
// Estrutura deste código:
//    Todo código está dentro da classe *Sistema*
//    Dentro de Sistema, encontra-se acima a definição de HW:
//           Memory,  Word, 
//           CPU tem Opcodes (codigos de operacoes suportadas na cpu),
//               e Interrupcoes possíveis, define o que executa para cada instrucao
//           VM -  a máquina virtual é uma instanciação de CPU e Memória
//    Depois as definições de SW:
//           no momento são esqueletos (so estrutura) para
//					InterruptHandling    e
//					SysCallHandling 
//    A seguir temos utilitários para usar o sistema
//           carga, início de execução e dump de memória
//    Por último os programas existentes, que podem ser copiados em memória.
//           Isto representa programas armazenados.
//    Veja o main.  Ele instancia o Sistema com os elementos mencionados acima.
//           em seguida solicita a execução de algum programa com  loadAndExec

import java.util.*;

import src.Sistema.CPU;
import src.Sistema.Interrupts;
import src.Sistema.Memory;
import src.Sistema.Opcode;
import src.Sistema.PCB;
import src.Sistema.Word;

public class Sistema {

	// -------------------------------------------------------------------------------------------------------
	// --------------------- H A R D W A R E - definicoes de HW
	// ----------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// --------------------- M E M O R I A - definicoes de palavra de memoria,
	// memória ----------------------

	private boolean isSchedulerAlive() {
  return schedThread != null && schedThread.isAlive();
}


	public class Memory {
		public Word[] pos; // pos[i] é a posição i da memória. cada posição é uma palavra.

		public Memory(int size) {
			pos = new Word[size];
			for (int i = 0; i < pos.length; i++) {
				pos[i] = new Word(Opcode.___, -1, -1, -1);
			}
			; // cada posicao da memoria inicializada
		}
	}

	public class Word {    // cada posicao da memoria tem uma instrucao (ou um dado)
		public Opcode opc; //
		public int ra;     // indice do primeiro registrador da operacao (Rs ou Rd cfe opcode na tabela)
		public int rb;     // indice do segundo registrador da operacao (Rc ou Rs cfe operacao)
		public int p;      // parametro para instrucao (k ou A cfe operacao), ou o dado, se opcode = DADO

		public Word(Opcode _opc, int _ra, int _rb, int _p) { // vide definição da VM - colunas vermelhas da tabela
			opc = _opc;
			ra = _ra;
			rb = _rb;
			p  = _p;
		}
	}

	// -------------------------------------------------------------------------------------------------------
	// --------------------- C P U - definicoes da CPU
	// -----------------------------------------------------

	public enum Opcode {
		DATA, ___,              // se memoria nesta posicao tem um dado, usa DATA, se nao usada ee NULO ___
		JMP, JMPI, JMPIG, JMPIL, JMPIE, // desvios
		JMPIM, JMPIGM, JMPILM, JMPIEM,
		JMPIGK, JMPILK, JMPIEK, JMPIGT,
		ADDI, SUBI, ADD, SUB, MULT,    // matematicos
		LDI, LDD, STD, LDX, STX, MOVE, // movimentacao
		SYSCALL, STOP                  // chamada de sistema e parada
	}

	public enum Interrupts {           
    noInterrupt, intEnderecoInvalido, intInstrucaoInvalida, intOverflow, intTimer;
}


	public class CPU {
		private int maxInt; // valores maximo e minimo para inteiros nesta cpu
		private int minInt;
		                    // CONTEXTO da CPU ...
		private int pc;     // ... composto de program counter,
		private Word ir;    // instruction register,
		private int[] reg;  // registradores da CPU
		private Interrupts irpt; // durante instrucao, interrupcao pode ser sinalizada
		                    // FIM CONTEXTO DA CPU: tudo que precisa sobre o estado de um processo para
		                    // executa-lo
		                    // nas proximas versoes isto pode modificar

		private Word[] m;   // m é o array de memória "física", CPU tem uma ref a m para acessar

		private InterruptHandling ih;    // significa desvio para rotinas de tratamento de Int - se int ligada, desvia
		private SysCallHandling sysCall; // significa desvio para tratamento de chamadas de sistema

		private boolean cpuStop;    // flag para parar CPU - caso de interrupcao que acaba o processo, ou chamada stop - 
									// nesta versao acaba o sistema no fim do prog

		                            // auxilio aa depuração
		private boolean debug;      // se true entao mostra cada instrucao em execucao
		private Utilities u;        // para debug (dump)

		private int delta = 8;   // ajuste o quantum aqui
		private int tick  = 0;
		

		public CPU(Memory _mem, boolean _debug) { // ref a MEMORIA passada na criacao da CPU
			maxInt = 32767;            // capacidade de representacao modelada
			minInt = -32767;           // se exceder deve gerar interrupcao de overflow
			m = _mem.pos;              // usa o atributo 'm' para acessar a memoria, só para ficar mais pratico
			reg = new int[10];         // aloca o espaço dos registradores - regs 8 e 9 usados somente para IO

			debug = _debug;            // se true, print da instrucao em execucao

		}





		public void setTimerDelta(int d) { this.delta = Math.max(1, d); }
		public void resetTicks() { this.tick = 0; }

		// ----- CONTEXTO para PCB -----
		public void saveContext(PCB pcb) {
			pcb.pcLogico = this.pc;
			System.arraycopy(this.reg, 0, pcb.regs, 0, this.reg.length);
		}
		public void loadContext(PCB pcb) {
			this.pc = pcb.pcLogico;
			System.arraycopy(pcb.regs, 0, this.reg, 0, this.reg.length);
			irpt = Interrupts.noInterrupt;
			resetTicks();
		}
		// ------------------------------

		public void setDebug(boolean d) { this.debug = d; }

		private int tamPg() { return u.hw.gm.getTamPg(); } // usa GM
		private int traduz(int enderecoLogico) {
			// Sem tabela ativa: endereço lógico==físico (modo legado)
			if (u.hw.tabelaPaginasAtiva == null) return enderecoLogico;

			int pagina = enderecoLogico / tamPg();
			int desloc = enderecoLogico % tamPg();
			if (pagina < 0 || pagina >= u.hw.tabelaPaginasAtiva.length) {
				irpt = Interrupts.intEnderecoInvalido;
				return -1;
			}
			int frame = u.hw.tabelaPaginasAtiva[pagina];
			if (frame < 0 || frame >= u.hw.gm.getNumFrames()) {
				irpt = Interrupts.intEnderecoInvalido;
				return -1;
			}
			int enderecoFisico = frame * tamPg() + desloc;
			if (enderecoFisico < 0 || enderecoFisico >= m.length) {
				irpt = Interrupts.intEnderecoInvalido;
				return -1;
			}
			return enderecoFisico;
		}
		public int readMemLogicaP(int e) {
			Word w = lerMemLogica(e);   // usa MMU
			return (w != null) ? w.p : 0;
		}
		public boolean writeMemLogicaP(int e, int valor) {
			if (legalLogico(e)) {
				escreverMemLogica(e, valor);
				return true;
			}
			return false;
		}



		private boolean legalLogico(int e) {
			int ef = traduz(e);
			return (ef >= 0); // traduz seta intEnderecoInvalido se ruim
		}

		private Word lerMemLogica(int e) {
			int ef = traduz(e);
			if (ef < 0) return null;
			return m[ef];
		}
		private void escreverMemLogica(int e, int valor) {
			int ef = traduz(e);
			if (ef < 0) return;
			m[ef].opc = Opcode.DATA;
			m[ef].p   = valor;
		}
	


		public void setAddressOfHandlers(InterruptHandling _ih, SysCallHandling _sysCall) {
			ih = _ih;                  // aponta para rotinas de tratamento de int
			sysCall = _sysCall;        // aponta para rotinas de tratamento de chamadas de sistema
		}

		public void setUtilities(Utilities _u) {
			u = _u;                     // aponta para rotinas utilitárias - fazer dump da memória na tela
		}


                                       // verificação de enderecamento 
		private boolean legal(int e) { // todo acesso a memoria tem que ser verificado se é válido - 
			                           // aqui no caso se o endereco é um endereco valido em toda memoria
			if (e >= 0 && e < m.length) {
				return true;
			} else {
				irpt = Interrupts.intEnderecoInvalido;    // se nao for liga interrupcao no meio da exec da instrucao
				return false;
			}
		}

		private boolean testOverflow(int v) {             // toda operacao matematica deve avaliar se ocorre overflow
			if ((v < minInt) || (v > maxInt)) {
				irpt = Interrupts.intOverflow;            // se houver liga interrupcao no meio da exec da instrucao
				return false;
			}
			;
			return true;
		}

		public void setContext(int _pc) {                 // usado para setar o contexto da cpu para rodar um processo
			                                              // [ nesta versao é somente colocar o PC na posicao 0 ]
			pc = _pc;                                     // pc cfe endereco logico
			irpt = Interrupts.noInterrupt;                // reset da interrupcao registrada
		}

		public void run() {                               // execucao da CPU supoe que o contexto da CPU, vide acima, 
														  // esta devidamente setado
			cpuStop = false;
			while (!cpuStop) {      // ciclo de instrucoes. acaba cfe resultado da exec da instrucao, veja cada caso.

				// --------------------------------------------------------------------------------------------------
				if (legalLogico(pc)) {
    			ir = lerMemLogica(pc); // busca via tradução
					             // resto é dump de debug
					if (debug) {
						System.out.print("                                              regs: ");
						for (int i = 0; i < 10; i++) {
							System.out.print(" r[" + i + "]:" + reg[i]);
						}
						;
						System.out.println();
					}
					if (debug) {
						System.out.print("                      pc: " + pc + "       exec: ");
						u.dump(ir);
					}

					switch (ir.opc) {       // conforme o opcode executa

						// Instrucoes de Busca e Armazenamento em Memoria
						case LDI: // Rd ← k        veja a tabela de instrucoes do HW simulado para entender a semantica da instrucao
							reg[ir.ra] = ir.p;
							pc++;
							break;
						case LDD: // Rd <- [A]
							if (legalLogico(ir.p)) {
								 reg[ir.ra] = lerMemLogica(ir.p).p;
								  pc++;
							}
							break;
						case LDX: // RD <- [RS] // NOVA
							if (legalLogico(reg[ir.rb])) {
								 reg[ir.ra] = lerMemLogica(reg[ir.rb]).p;
								  pc++;
							}
							break;
						case STD: // [A] ← Rs
							if (legalLogico(ir.p)) {
								escreverMemLogica(ir.p, reg[ir.ra]);
								pc++;
                                if (debug) 
								    {   System.out.print("                                  ");   
									    u.dump(ir.p,ir.p+1);							
									}
								}
							break;
						case STX: // [Rd] ←Rs
							if (legalLogico(reg[ir.ra])) {
								escreverMemLogica(reg[ir.ra], reg[ir.rb]);
								pc++;
							}

							;
							break;
						case MOVE: // RD <- RS
							reg[ir.ra] = reg[ir.rb];
							pc++;
							break;
						// Instrucoes Aritmeticas
						case ADD: // Rd ← Rd + Rs
							reg[ir.ra] = reg[ir.ra] + reg[ir.rb];
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case ADDI: // Rd ← Rd + k
							reg[ir.ra] = reg[ir.ra] + ir.p;
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case SUB: // Rd ← Rd - Rs
							reg[ir.ra] = reg[ir.ra] - reg[ir.rb];
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case SUBI: // RD <- RD - k // NOVA
							reg[ir.ra] = reg[ir.ra] - ir.p;
							testOverflow(reg[ir.ra]);
							pc++;
							break;
						case MULT: // Rd <- Rd * Rs
							reg[ir.ra] = reg[ir.ra] * reg[ir.rb];
							testOverflow(reg[ir.ra]);
							pc++;
							break;

						// Instrucoes JUMP
						case JMP: // PC <- k
							pc = ir.p;
							break;
						case JMPIM: // PC <- [A]
						// ANTES: pc = m[ir.p].p;
						if (legalLogico(ir.p)) {
							pc = lerMemLogica(ir.p).p;
						}
						break;
						case JMPIG: // If Rc > 0 Then PC ← Rs Else PC ← PC +1
							if (reg[ir.rb] > 0) {
								pc = reg[ir.ra];
							} else {
								pc++;
							}
							break;
						case JMPIGK: // If RC > 0 then PC <- k else PC++
							if (reg[ir.rb] > 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case JMPILK: // If RC < 0 then PC <- k else PC++
							if (reg[ir.rb] < 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case JMPIEK: // If RC = 0 then PC <- k else PC++
							if (reg[ir.rb] == 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case JMPIL: // if Rc < 0 then PC <- Rs Else PC <- PC +1
							if (reg[ir.rb] < 0) {
								pc = reg[ir.ra];
							} else {
								pc++;
							}
							break;
						case JMPIE: // If Rc = 0 Then PC <- Rs Else PC <- PC +1
							if (reg[ir.rb] == 0) {
								pc = reg[ir.ra];
							} else {
								pc++;
							}
							break;
						case JMPIGM: // If RC > 0 then PC <- [A] else PC++
						if (legalLogico(ir.p)) {
							if (reg[ir.rb] > 0) {
								pc = lerMemLogica(ir.p).p;
							} else {
								pc++;
							}
						}
						break;
						case JMPILM: // If RC < 0 then PC <- [A] else PC++
						if (legalLogico(ir.p)) {
							if (reg[ir.rb] < 0) {
								pc = lerMemLogica(ir.p).p;
							} else {
								pc++;
							}
						}
						break;
						case JMPIEM: // If RC = 0 then PC <- [A] else PC++
						if (legalLogico(ir.p)) {
							if (reg[ir.rb] == 0) {
								pc = lerMemLogica(ir.p).p;
							} else {
								pc++;
							}
						}
						break;
						case JMPIGT: // If RS>RC then PC <- k else PC++
							if (reg[ir.ra] > reg[ir.rb]) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						case DATA: // pc está sobre área supostamente de dados
							irpt = Interrupts.intInstrucaoInvalida;
							break;

						// Chamadas de sistema
						case SYSCALL:
							sysCall.handle(); // <<<<< aqui desvia para rotina de chamada de sistema, no momento so
												// temos IO
							pc++;
							break;

						case STOP: // por enquanto, para execucao
							if (!sysCall.stop()) {  // stop() agora devolve boolean
								cpuStop = true;     // false = não há mais quem rodar, pode parar a CPU
							}
							break;

						// Inexistente
						default:
							irpt = Interrupts.intInstrucaoInvalida;
							break;
					}
					// TIMER: após a instrução executar com sucesso
						if (irpt == Interrupts.noInterrupt) {
							if (++tick >= delta) {
								irpt = Interrupts.intTimer;
								tick = 0;
							}
						}

				}
				// --------------------------------------------------------------------------------------------------
				// VERIFICA INTERRUPÇÃO !!! - TERCEIRA FASE DO CICLO DE INSTRUÇÕES
				if (irpt != Interrupts.noInterrupt) { // existe interrupção
					boolean keepRunning = ih.handle(irpt); // handler agora retorna se a CPU deve continuar
					irpt = Interrupts.noInterrupt;
					if (!keepRunning) {
						cpuStop = true; // faults param; timer pode continuar sem parar
					}
				}


			} // FIM DO CICLO DE UMA INSTRUÇÃO
		}
	}
	// ------------------ C P U - fim
	// -----------------------------------------------------------------------
	// ------------------------------------------------------------------------------------------------------

	// ------------------- HW - constituido de CPU e MEMORIA
	// -----------------------------------------------
	class HW {
    public Memory mem;
    public CPU cpu;
    public GerenteMemoria gm;       // << novo
    public int tamPg = 4;           // << defina um default (pode vir de args)
    public int[] tabelaPaginasAtiva; // << tabela do “processo atual” (Parte A: 1 processo)

    public HW(int tamMem) {
        mem = new Memory(tamMem);
        gm  = new GerenteMemoria(tamMem, tamPg);
        cpu = new CPU(mem, true); // true liga debug
    }
}

	// -------------------------------------------------------------------------------------------------------

	// --------------------H A R D W A R E - fim
	// -------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// ///////////////////////////////////////////////////////////////////////////////////////////////////////

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// ------------------- SW - inicio - Sistema Operacional
	// -------------------------------------------------

	// ------------------- I N T E R R U P C O E S - rotinas de tratamento
	// ----------------------------------
	public class InterruptHandling {
    private HW hw;
    private GerenteProcessos gp; // setado depois

    public InterruptHandling(HW _hw) { hw = _hw; }
    public void setGP(GerenteProcessos gp) { this.gp = gp; }

   
		public boolean handle(Interrupts irpt) {
			if (irpt == Interrupts.intTimer) {
				gp.onTimeSlice();  // salva contexto do atual, coloca em READY e mete o proximo

				
				if (continuousOn) {
					return false;              // faz cpu.run() sair agora
				} else {
					return gp.hasRunnable();   // modo batch/exec: segue rodando
				}

			} else {
				if (!continuousOn) {
					System.out.println("Interrupcao " + irpt + " no PID " + (gp != null && gp.running != null ? gp.running.pid : -1));
				}
				if (gp != null && gp.running != null) {
					gp.onProcessFault();
					return gp.hasRunnable();
				}
				return false;
			}
		}


}


	// ------------------- C H A M A D A S D E S I S T E M A - rotinas de tratamento
	// ----------------------
	public class SysCallHandling {
    private HW hw;
    private GerenteProcessos gp; // setado depois

    public SysCallHandling(HW _hw) { hw = _hw; }
    public void setGP(GerenteProcessos gp) { this.gp = gp; }

	public boolean stop() {
		if (!(continuousOn || isSchedulerAlive())) {    
			System.out.println("                                               SYSCALL STOP");
		}
		if (gp != null) {
			gp.onProcessStop();
			return gp.hasRunnable();
		}
		return false;
		}

	public void handle() {
		boolean quiet = (continuousOn || isSchedulerAlive());   

		if (!quiet) {
			System.out.println("SYSCALL pars:  " + hw.cpu.reg[8] + " / " + hw.cpu.reg[9]);
		}

		if (hw.cpu.reg[8] == 1) {  // READ
			int v;
			if (quiet) {             // não bloqueia/sem log
			v = 0;
			} else {
			System.out.print("IN: ");
			try {
				java.util.Scanner sc = new java.util.Scanner(System.in);
				v = sc.nextInt();
			} catch (Exception e) {
				System.out.println("Leitura inválida; usando 0.");
				v = 0;
			}
			}
			int logicalAddr = hw.cpu.reg[9];
			if (!hw.cpu.writeMemLogicaP(logicalAddr, v)) {
			if (!quiet) System.out.println("END LOGICO INVALIDO na SYSCALL READ: " + logicalAddr);
			}

		} else if (hw.cpu.reg[8] == 2) { // WRITE
			int logicalAddr = hw.cpu.reg[9];
			int v = hw.cpu.readMemLogicaP(logicalAddr);
			if (!quiet) System.out.println("OUT:   " + v);

		} else {
			if (!quiet) System.out.println("  PARAMETRO INVALIDO");
		}
		}


}


	// ------------------ U T I L I T A R I O S D O S I S T E M A
	// -----------------------------------------
	// ------------------ load é invocado a partir de requisição do usuário

	// carga na memória
	public class Utilities {
		private HW hw;

		public Utilities(HW _hw) {
			hw = _hw;
		}

		private void loadProgram(Word[] p) {
			Word[] m = hw.mem.pos; // m[] é o array de posições memória do hw
			for (int i = 0; i < p.length; i++) {
				m[i].opc = p[i].opc;
				m[i].ra = p[i].ra;
				m[i].rb = p[i].rb;
				m[i].p = p[i].p;
			}
		}

private void loadProgramPaged(Word[] progImage) {
    int tamProg = progImage.length;
    int[] tabela = hw.gm.aloca(tamProg);
    if (tabela == null) {
        System.out.println("Sem memória (frames) para alocar programa.");
        return;
    }
    hw.tabelaPaginasAtiva = tabela; 

	System.out.println("Frames alocados: " + java.util.Arrays.toString(hw.tabelaPaginasAtiva));
    System.out.println("tamPg = " + hw.gm.getTamPg() + "  |  páginas = " + tabela.length);

    int tamPg = hw.gm.getTamPg();
    int posLogica = 0;
    for (int p = 0; p < tabela.length; p++) {
        int frame = tabela[p];
        int baseFisica = frame * tamPg;
        for (int off = 0; off < tamPg && posLogica < tamProg; off++, posLogica++) {
            // copia a “imagem lgoica” para o frame físico correto
            hw.mem.pos[baseFisica + off].opc = progImage[posLogica].opc;
            hw.mem.pos[baseFisica + off].ra  = progImage[posLogica].ra;
            hw.mem.pos[baseFisica + off].rb  = progImage[posLogica].rb;
            hw.mem.pos[baseFisica + off].p   = progImage[posLogica].p;
        }
    }
    // ponto de entrada lógico do programa é 0
    hw.cpu.setContext(0);
}

		

		// dump da memória
		public void dump(Word w) { 
			System.out.print("[ ");
			System.out.print(w.opc);
			System.out.print(", ");
			System.out.print(w.ra);
			System.out.print(", ");
			System.out.print(w.rb);
			System.out.print(", ");
			System.out.print(w.p);
			System.out.println("  ] ");
		}

		public void dump(int ini, int fim) {
			Word[] m = hw.mem.pos; // m[] é o array de posições memória do hw
			for (int i = ini; i < fim; i++) {
				System.out.print(i);
				System.out.print(":  ");
				dump(m[i]);
			}
		}

		private void loadAndExec(Word[] p) {
		loadProgramPaged(p);                     
		System.out.println("---------------------------------- programa carregado na memoria");
		System.out.println("---------------------------------- inicia execucao ");
		hw.cpu.run();
		System.out.println("---------------------------------- fim da execucao ");
}


	}

	public class SO {
		public InterruptHandling ih;
		public SysCallHandling sc;
		public Utilities utils;

		public SO(HW hw) {
			ih = new InterruptHandling(hw); // rotinas de tratamento de int
			sc = new SysCallHandling(hw); // chamadas de sistema
			hw.cpu.setAddressOfHandlers(ih, sc);
			utils = new Utilities(hw);
		}
	}
	// -------------------------------------------------------------------------------------------------------
	// ------------------- S I S T E M A
	// --------------------------------------------------------------------

	public HW hw;
	public SO so;
	public Programs progs;

	
	public GerenteProcessos gp;


	public Sistema(int tamMem) {
		hw = new HW(tamMem);           // memoria do HW tem tamMem palavras
		so = new SO(hw);
		hw.cpu.setUtilities(so.utils); // permite cpu fazer dump de memoria ao avancar
		progs = new Programs();
		gp = new GerenteProcessos(hw, so.utils, progs);
		so.ih.setGP(gp);
		so.sc.setGP(gp);

		
	}

	private Thread schedThread;
  private volatile boolean continuousOn = false;

  private void startContinuous() {
    if (schedThread != null && schedThread.isAlive()) {
      System.out.println("[SCH] modo contínuo já está ON");
      return;
    }
    continuousOn = true;

	gp.setTrace(false); // garante traceOff pra nao floodar o temrinal

    schedThread = new Thread(() -> {
      System.out.println("[SCH] modo contínuo ON");
      while (continuousOn) {
        try {
        boolean temAlgo = gp.hasRunnable();
          if (!temAlgo) {
            Thread.sleep(20);     // espera um pouquinho
            continue;
          }
			gp.kick();           // garante o primeiro dispatch
          // Vai rodar até não haver mais processos
          hw.cpu.run();
        try { Thread.sleep(200); } catch (InterruptedException ie) { break; } //tempo pra ver no terminal o running se nao ele acaba mtn rapido

         
        } catch (InterruptedException ie) {

          break;
        } catch (Throwable t) {
          t.printStackTrace();
        }
      }
      System.out.println("[SCH] modo contínuo OFF");
    });
    schedThread.setDaemon(true);
    schedThread.start();
  }

  private void stopContinuous() {
    if (!continuousOn) {
      System.out.println("[SCH] modo contínuo já está OFF");
      return;
    }
    continuousOn = false;
    if (schedThread != null) schedThread.interrupt();

	    gp.forcePauseRunning();

  }

	public void run() {
	System.out.println("SO pronto. Comandos: new <prog> | rm <pid> | ps | dump <pid> | dumpM <ini> <fim> | exec <pid> | execAll | traceOn | traceOff | go | halt | exit");
	Scanner sc = new Scanner(System.in);
	while (true) {
		System.out.print("> ");
		if (!sc.hasNext()) break;
		String cmd = sc.next();

		if (cmd.equalsIgnoreCase("new")) {
		String prog = sc.next();
		gp.newProcess(prog);           // em modo contínuo, o timer preempta e pega sozinho

		} else if (cmd.equalsIgnoreCase("rm")) {
		int pid = sc.nextInt();
		gp.rm(pid);

		} else if (cmd.equalsIgnoreCase("ps")) {
		gp.ps();

		} else if (cmd.equalsIgnoreCase("dump")) {
		int pid = sc.nextInt();
		gp.dump(pid);

		} else if (cmd.equalsIgnoreCase("dumpM")) {
		int ini = sc.nextInt();
		int fim = sc.nextInt();
		gp.dumpM(ini, fim);

		} else if (cmd.equalsIgnoreCase("exec")) {
		int pid = sc.nextInt();
		if (continuousOn) {
			System.out.println("[WARN] Modo contínuo ON: ignore 'exec'. O escalonador já está rodando.");
		} else {
			gp.exec(pid); // modo batch: bloqueia ate pausar/terminar
		}

		} else if (cmd.equalsIgnoreCase("execAll")) {
		if (continuousOn) {
			System.out.println("[WARN] Modo contínuo ON: ignore 'execAll'. Use 'halt' para voltar ao modo batch.");
		} else {
			gp.execAll();  // roda tudo e volta
		}

		} else if (cmd.equalsIgnoreCase("traceOn")) {
		gp.setTrace(true);

		} else if (cmd.equalsIgnoreCase("traceOff")) {
		gp.setTrace(false);

		} else if (cmd.equalsIgnoreCase("go")) {
		startContinuous();

		} else if (cmd.equalsIgnoreCase("halt")) {
		stopContinuous();

		} else if (cmd.equalsIgnoreCase("exit")) {
		stopContinuous();   // garante desligar a thread
		System.out.println("Encerrando SO.");
		break;

		} else {
		System.out.println("Comando inválido.");
		sc.nextLine();
		}
	}
	sc.close();
	}


	// ------------------- S I S T E M A - fim
	// --------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// ------------------- instancia e testa sistema
	public static void main(String args[]) {
		Sistema s = new Sistema(1024);
		s.run();
	}

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// --------------- P R O G R A M A S - não fazem parte do sistema
	// esta classe representa programas armazenados (como se estivessem em disco)
	// que podem ser carregados para a memória (load faz isto)

	public class Program {
		public String name;
		public Word[] image;

		public Program(String n, Word[] i) {
			name = n;
			image = i;
		}
	}

	public class Programs {

		public Word[] retrieveProgram(String pname) {
			for (Program p : progs) {
				if (p != null && p.name.equals(pname))
					return p.image;
			}
			return null;
		}

		public Program[] progs = {
				new Program("fatorial",
						new Word[] {
								// este fatorial so aceita valores positivos. nao pode ser zero
								// linha coment
								new Word(Opcode.LDI, 0, -1, 7), // 0 r0 é valor a calcular fatorial
								new Word(Opcode.LDI, 1, -1, 1), // 1 r1 é 1 para multiplicar (por r0)
								new Word(Opcode.LDI, 6, -1, 1), // 2 r6 é 1 o decremento
								new Word(Opcode.LDI, 7, -1, 8), // 3 r7 tem posicao 8 para fim do programa
								new Word(Opcode.JMPIE, 7, 0, 0), // 4 se r0=0 pula para r7(=8)
								new Word(Opcode.MULT, 1, 0, -1), // 5 r1 = r1 * r0 (r1 acumula o produto por cada termo)
								new Word(Opcode.SUB, 0, 6, -1), // 6 r0 = r0 - r6 (r6=1) decrementa r0 para proximo
																// termo
								new Word(Opcode.JMP, -1, -1, 4), // 7 vai p posicao 4
								new Word(Opcode.STD, 1, -1, 10), // 8 coloca valor de r1 na posição 10
								new Word(Opcode.STOP, -1, -1, -1), // 9 stop
								new Word(Opcode.DATA, -1, -1, -1) // 10 ao final o valor está na posição 10 da memória
						}),

				new Program("fatorialV2",
						new Word[] {
								new Word(Opcode.LDI, 0, -1, 5), // numero para colocar na memoria, ou pode ser lido
								new Word(Opcode.STD, 0, -1, 19),
								new Word(Opcode.LDD, 0, -1, 19),
								new Word(Opcode.LDI, 1, -1, -1),
								new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
								new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
								new Word(Opcode.LDI, 1, -1, 1),
								new Word(Opcode.LDI, 6, -1, 1),
								new Word(Opcode.LDI, 7, -1, 13),
								new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula para STD (Stop-1)
								new Word(Opcode.MULT, 1, 0, -1),
								new Word(Opcode.SUB, 0, 6, -1),
								new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
								new Word(Opcode.STD, 1, -1, 18),
								new Word(Opcode.LDI, 8, -1, 2), // escrita
								new Word(Opcode.LDI, 9, -1, 18), // endereco com valor a escrever
								new Word(Opcode.SYSCALL, -1, -1, -1),
								new Word(Opcode.STOP, -1, -1, -1), // POS 17
								new Word(Opcode.DATA, -1, -1, -1), // POS 18
								new Word(Opcode.DATA, -1, -1, -1) } // POS 19
				),

				new Program("progMinimo",
						new Word[] {
								new Word(Opcode.LDI, 0, -1, 999),
								new Word(Opcode.STD, 0, -1, 8),
								new Word(Opcode.STD, 0, -1, 9),
								new Word(Opcode.STD, 0, -1, 10),
								new Word(Opcode.STD, 0, -1, 11),
								new Word(Opcode.STD, 0, -1, 12),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1), // 7
								new Word(Opcode.DATA, -1, -1, -1), // 8
								new Word(Opcode.DATA, -1, -1, -1), // 9
								new Word(Opcode.DATA, -1, -1, -1), // 10
								new Word(Opcode.DATA, -1, -1, -1), // 11
								new Word(Opcode.DATA, -1, -1, -1), // 12
								new Word(Opcode.DATA, -1, -1, -1) // 13
						}),

				new Program("fibonacci10",
						new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.STD, 1, -1, 20),
								new Word(Opcode.LDI, 2, -1, 1),
								new Word(Opcode.STD, 2, -1, 21),
								new Word(Opcode.LDI, 0, -1, 22),
								new Word(Opcode.LDI, 6, -1, 6),
								new Word(Opcode.LDI, 7, -1, 31),
								new Word(Opcode.LDI, 3, -1, 0),
								new Word(Opcode.ADD, 3, 1, -1),
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.ADD, 1, 2, -1),
								new Word(Opcode.ADD, 2, 3, -1),
								new Word(Opcode.STX, 0, 2, -1),
								new Word(Opcode.ADDI, 0, -1, 1),
								new Word(Opcode.SUB, 7, 0, -1),
								new Word(Opcode.JMPIG, 6, 7, -1),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1), // POS 20
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1) // ate aqui - serie de fibonacci ficara armazenada
						}),

				new Program("fibonacci10v2",
						new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.STD, 1, -1, 20),
								new Word(Opcode.LDI, 2, -1, 1),
								new Word(Opcode.STD, 2, -1, 21),
								new Word(Opcode.LDI, 0, -1, 22),
								new Word(Opcode.LDI, 6, -1, 6),
								new Word(Opcode.LDI, 7, -1, 31),
								new Word(Opcode.MOVE, 3, 1, -1),
								new Word(Opcode.MOVE, 1, 2, -1),
								new Word(Opcode.ADD, 2, 3, -1),
								new Word(Opcode.STX, 0, 2, -1),
								new Word(Opcode.ADDI, 0, -1, 1),
								new Word(Opcode.SUB, 7, 0, -1),
								new Word(Opcode.JMPIG, 6, 7, -1),
								new Word(Opcode.STOP, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1), // POS 20
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1) // ate aqui - serie de fibonacci ficara armazenada
						}),
				new Program("fibonacciREAD",
						new Word[] {
								// mesmo que prog exemplo, so que usa r0 no lugar de r8
								new Word(Opcode.LDI, 8, -1, 1), // leitura
								new Word(Opcode.LDI, 9, -1, 55), // endereco a guardar o tamanho da serie de fib a gerar
																	// - pode ser de 1 a 20
								new Word(Opcode.SYSCALL, -1, -1, -1),
								new Word(Opcode.LDD, 7, -1, 55),
								new Word(Opcode.LDI, 3, -1, 0),
								new Word(Opcode.ADD, 3, 7, -1),
								new Word(Opcode.LDI, 4, -1, 36), // posicao para qual ira pular (stop) *
								new Word(Opcode.LDI, 1, -1, -1), // caso negativo
								new Word(Opcode.STD, 1, -1, 41),
								new Word(Opcode.JMPIL, 4, 7, -1), // pula pra stop caso negativo *
								new Word(Opcode.JMPIE, 4, 7, -1), // pula pra stop caso 0
								new Word(Opcode.ADDI, 7, -1, 41), // fibonacci + posição do stop
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.STD, 1, -1, 41), // 25 posicao de memoria onde inicia a serie de
																	// fibonacci gerada
								new Word(Opcode.SUBI, 3, -1, 1), // se 1 pula pro stop
								new Word(Opcode.JMPIE, 4, 3, -1),
								new Word(Opcode.ADDI, 3, -1, 1),
								new Word(Opcode.LDI, 2, -1, 1),
								new Word(Opcode.STD, 2, -1, 42),
								new Word(Opcode.SUBI, 3, -1, 2), // se 2 pula pro stop
								new Word(Opcode.JMPIE, 4, 3, -1),
								new Word(Opcode.LDI, 0, -1, 43),
								new Word(Opcode.LDI, 6, -1, 25), // salva posição de retorno do loop
								new Word(Opcode.LDI, 5, -1, 0), // salva tamanho
								new Word(Opcode.ADD, 5, 7, -1),
								new Word(Opcode.LDI, 7, -1, 0), // zera (inicio do loop)
								new Word(Opcode.ADD, 7, 5, -1), // recarrega tamanho
								new Word(Opcode.LDI, 3, -1, 0),
								new Word(Opcode.ADD, 3, 1, -1),
								new Word(Opcode.LDI, 1, -1, 0),
								new Word(Opcode.ADD, 1, 2, -1),
								new Word(Opcode.ADD, 2, 3, -1),
								new Word(Opcode.STX, 0, 2, -1),
								new Word(Opcode.ADDI, 0, -1, 1),
								new Word(Opcode.SUB, 7, 0, -1),
								new Word(Opcode.JMPIG, 6, 7, -1), // volta para o inicio do loop
								new Word(Opcode.STOP, -1, -1, -1), // POS 36
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1), // POS 41
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1)
						}),
				new Program("PB",
						new Word[] {
								// dado um inteiro em alguma posição de memória,
								// se for negativo armazena -1 na saída; se for positivo responde o fatorial do
								// número na saída
								new Word(Opcode.LDI, 0, -1, 7), // numero para colocar na memoria
								new Word(Opcode.STD, 0, -1, 50),
								new Word(Opcode.LDD, 0, -1, 50),
								new Word(Opcode.LDI, 1, -1, -1),
								new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
								new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
								new Word(Opcode.LDI, 1, -1, 1),
								new Word(Opcode.LDI, 6, -1, 1),
								new Word(Opcode.LDI, 7, -1, 13),
								new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula pra STD (Stop-1)
								new Word(Opcode.MULT, 1, 0, -1),
								new Word(Opcode.SUB, 0, 6, -1),
								new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
								new Word(Opcode.STD, 1, -1, 15),
								new Word(Opcode.STOP, -1, -1, -1), // POS 14
								new Word(Opcode.DATA, -1, -1, -1) // POS 15
						}),
				new Program("PC",
						new Word[] {
								// Para um N definido (10 por exemplo)
								// o programa ordena um vetor de N números em alguma posição de memória;
								// ordena usando bubble sort
								// loop ate que não swap nada
								// passando pelos N valores
								// faz swap de vizinhos se da esquerda maior que da direita
								new Word(Opcode.LDI, 7, -1, 5), // TAMANHO DO BUBBLE SORT (N)
								new Word(Opcode.LDI, 6, -1, 5), // aux N
								new Word(Opcode.LDI, 5, -1, 46), // LOCAL DA MEMORIA
								new Word(Opcode.LDI, 4, -1, 47), // aux local memoria
								new Word(Opcode.LDI, 0, -1, 4), // colocando valores na memoria
								new Word(Opcode.STD, 0, -1, 46),
								new Word(Opcode.LDI, 0, -1, 3),
								new Word(Opcode.STD, 0, -1, 47),
								new Word(Opcode.LDI, 0, -1, 5),
								new Word(Opcode.STD, 0, -1, 48),
								new Word(Opcode.LDI, 0, -1, 1),
								new Word(Opcode.STD, 0, -1, 49),
								new Word(Opcode.LDI, 0, -1, 2),
								new Word(Opcode.STD, 0, -1, 50), // colocando valores na memoria até aqui - POS 13
								new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 1
								new Word(Opcode.STD, 3, -1, 99),
								new Word(Opcode.LDI, 3, -1, 22), // Posicao para pulo CHAVE 2
								new Word(Opcode.STD, 3, -1, 98),
								new Word(Opcode.LDI, 3, -1, 38), // Posicao para pulo CHAVE 3
								new Word(Opcode.STD, 3, -1, 97),
								new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 4 (não usada)
								new Word(Opcode.STD, 3, -1, 96),
								new Word(Opcode.LDI, 6, -1, 0), // r6 = r7 - 1 POS 22
								new Word(Opcode.ADD, 6, 7, -1),
								new Word(Opcode.SUBI, 6, -1, 1), // ate aqui
								new Word(Opcode.JMPIEM, -1, 6, 97), // CHAVE 3 para pular quando r7 for 1 e r6 0 para
																	// interomper o loop de vez do programa
								new Word(Opcode.LDX, 0, 5, -1), // r0 e ra pegando valores das posições da memoria POS
																// 26
								new Word(Opcode.LDX, 1, 4, -1),
								new Word(Opcode.LDI, 2, -1, 0),
								new Word(Opcode.ADD, 2, 0, -1),
								new Word(Opcode.SUB, 2, 1, -1),
								new Word(Opcode.ADDI, 4, -1, 1),
								new Word(Opcode.SUBI, 6, -1, 1),
								new Word(Opcode.JMPILM, -1, 2, 99), // LOOP chave 1 caso neg procura prox
								new Word(Opcode.STX, 5, 1, -1),
								new Word(Opcode.SUBI, 4, -1, 1),
								new Word(Opcode.STX, 4, 0, -1),
								new Word(Opcode.ADDI, 4, -1, 1),
								new Word(Opcode.JMPIGM, -1, 6, 99), // LOOP chave 1 POS 38
								new Word(Opcode.ADDI, 5, -1, 1),
								new Word(Opcode.SUBI, 7, -1, 1),
								new Word(Opcode.LDI, 4, -1, 0), // r4 = r5 + 1 POS 41
								new Word(Opcode.ADD, 4, 5, -1),
								new Word(Opcode.ADDI, 4, -1, 1), // ate aqui
								new Word(Opcode.JMPIGM, -1, 7, 98), // LOOP chave 2
								new Word(Opcode.STOP, -1, -1, -1), // POS 45
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1),
								new Word(Opcode.DATA, -1, -1, -1)
						})
		};
	}

	public enum ProcState { NEW, READY, RUNNING, TERMINATED }

	public class PCB {
    public final int pid;
    public final String name;
    public ProcState state;
    public int pcLogico;          // PC logico
    public int[] tabelaPaginas;   // page -> frame
    public int tamProg;           // numero de palavras do programa
    public int[] regs = new int[10]; 

    public PCB(int pid, String name, int[] tabela, int tamProg) {
        this.pid = pid;
        this.name = name;
        this.tabelaPaginas = tabela;
        this.tamProg = tamProg;
        this.pcLogico = 0;
        this.state = ProcState.NEW;
    }
}


	public class GerenteProcessos {
		private final HW hw;
		private final Utilities utils;
		private final Programs progs;
		private int nextPid = 1;

		private final Map<Integer, PCB> procTable = new HashMap<>();
		private final Deque<PCB> readyQueue = new ArrayDeque<>();
		private PCB running = null;

		public GerenteProcessos(HW hw, Utilities utils, Programs progs) {
			this.hw = hw;
			this.utils = utils;
			this.progs = progs;
		}

		public synchronized void forcePauseRunning() {
			if (running != null) {
				hw.cpu.saveContext(running);     // guarda PC e regs
				running.state = ProcState.READY; // volta pra READY
				readyQueue.addLast(running);     // re-enfileira
				running = null;
				hw.tabelaPaginasAtiva = null;    // sem processo ativo
			}
		}

		public synchronized void kick() {
			if (running == null) {
				scheduleNext();
			}
			}


		public synchronized void onProcessFault() {
			PCB fin = running;
			fin.state = ProcState.TERMINATED;
			hw.gm.desaloca(fin.tabelaPaginas);
			procTable.remove(fin.pid);
			running = null;
			scheduleNext();
			}

		public synchronized void onTimeSlice() {
			if (running == null) return;
			hw.cpu.saveContext(running);
			running.state = ProcState.READY;
			readyQueue.addLast(running);
			scheduleNext();
		}

		public synchronized void onProcessStop() {
			if (running == null) return;
			PCB fin = running;
			fin.state = ProcState.TERMINATED;
			hw.gm.desaloca(fin.tabelaPaginas);
			procTable.remove(fin.pid);
			running = null;
			scheduleNext();
		}

		public synchronized boolean hasRunnable() {
			return running != null || !readyQueue.isEmpty();
		}

		private synchronized void dispatch(PCB pcb) {
			running = pcb;
			running.state = ProcState.RUNNING;
			hw.tabelaPaginasAtiva = running.tabelaPaginas;
			hw.cpu.loadContext(running);
		}
		private synchronized void scheduleNext() {
			PCB next = readyQueue.pollFirst();
			if (next != null) {
				dispatch(next);
			} else {
				hw.tabelaPaginasAtiva = null;
				running = null;
			}
		}


		// new nome do pgrograma
		public synchronized int newProcess(String progName) {
			Word[] image = progs.retrieveProgram(progName);
			if (image == null) {
				System.out.println("Programa não encontrado: " + progName);
				return -1;
			}
			int tamProg = image.length;

			//  reserva espaço logico até 100.
			int tamLogico = Math.max(tamProg, 100);
			int[] tabela = hw.gm.aloca(tamLogico);
			if (tabela == null) {
				System.out.println("Sem memória (frames) para alocar: " + progName);
				return -1;
			}

			copyProgramToFrames(tabela, image);

			int pid = nextPid++;
			PCB pcb = new PCB(pid, progName, tabela, tamProg);
			pcb.state = ProcState.READY;
			procTable.put(pid, pcb);
			readyQueue.addLast(pcb);

			System.out.println("Processo criado: PID=" + pid + "  Prog=" + progName +
					"  Pags=" + tabela.length + "  Frames=" + java.util.Arrays.toString(tabela));
			return pid;
		}

		// rm id do programa
		public synchronized boolean rm(int pid) {
			PCB pcb = procTable.get(pid);
			if (pcb == null) {
				System.out.println("PID inexistente: " + pid);
				return false;
			}
			if (running != null && running.pid == pid) {
				System.out.println("PID " + pid + " está rodando; finalize antes de remover.");
				return false;
			}
			readyQueue.removeIf(p -> p.pid == pid);
			hw.gm.desaloca(pcb.tabelaPaginas);
			procTable.remove(pid);
			System.out.println("Processo removido: PID=" + pid + " (" + pcb.name + ")");
			return true;
		}

		// ps
		public synchronized void ps() {
			if (procTable.isEmpty()) {
				System.out.println("(sem processos)");
				return;
			}
			System.out.println(String.format("%-5s %-14s %-10s %-6s %-18s", "PID", "PROGRAMA", "ESTADO", "PC", "PAGS(frames)"));
			for (PCB p : procTable.values()) {
				System.out.println(String.format("%-5d %-14s %-10s %-6d %s",
						p.pid, p.name, p.state, p.pcLogico, java.util.Arrays.toString(p.tabelaPaginas)));
			}
			if (running != null) {
				System.out.println("Running: PID=" + running.pid);
			}
			if (!readyQueue.isEmpty()) {
				System.out.print("ReadyQueue: ");
				readyQueue.forEach(p -> System.out.print(p.pid + " "));
				System.out.println();
			}
		}

		// dump id do programa
		public synchronized void dump(int pid) {
			PCB pcb = procTable.get(pid);
			if (pcb == null) {
				System.out.println("PID inexistente: " + pid);
				return;
			}
			System.out.println("PCB { pid=" + pcb.pid + ", name=" + pcb.name + ", state=" + pcb.state +
					", pcLogico=" + pcb.pcLogico + ", tamProg=" + pcb.tamProg + ", tabela=" +
					java.util.Arrays.toString(pcb.tabelaPaginas) + " }");

			int tamPg = hw.gm.getTamPg();
			System.out.println("Dump memória física por frame:");
			for (int frame : pcb.tabelaPaginas) {
				int base = frame * tamPg;
				System.out.println("Frame " + frame + " (físico " + base + " .. " + (base + tamPg - 1) + "):");
				utils.dump(base, base + tamPg);
			}
		}

		// dumpM inicio fim
		public synchronized void dumpM(int ini, int fim) {
			utils.dump(ini, fim);
		}

		// exec id do programa
		public synchronized boolean exec(int pid) {
			PCB pcb = procTable.get(pid);
			if (pcb == null) { System.out.println("PID inexistente: " + pid); return false; }
			if (running != null) { System.out.println("Já existe processo rodando: PID=" + running.pid); return false; }
			readyQueue.removeIf(p -> p.pid == pid);

			dispatch(pcb);
			System.out.println("---------------------------------- inicia execucao PID=" + pid);
			hw.cpu.run(); // timer/stop mudarão o running e continuarão via handlers
			System.out.println("---------------------------------- pausa/termino PID=" + pid);
			return true;
		}
		public synchronized void execAll() {
			while (hasRunnable()) {
				if (running == null) scheduleNext();
				if (running == null) break;
				hw.cpu.run();
			}
			System.out.println("[execAll] Todos os processos finalizaram ou foram removidos.");
		}



		// traceOn / traceOff
		public synchronized void setTrace(boolean on) {
			hw.cpu.setDebug(on);
			System.out.println("Trace " + (on ? "ON" : "OFF"));
		}

		// copia a imagem logica do programa para os frames fisicos alocados
		private void copyProgramToFrames(int[] tabela, Word[] progImage) {
			int tamPg = hw.gm.getTamPg();
			int posLog = 0;
			for (int p = 0; p < tabela.length; p++) {
				int frame = tabela[p];
				int baseFisica = frame * tamPg;
				for (int off = 0; off < tamPg && posLog < progImage.length; off++, posLog++) {
					hw.mem.pos[baseFisica + off].opc = progImage[posLog].opc;
					hw.mem.pos[baseFisica + off].ra  = progImage[posLog].ra;
					hw.mem.pos[baseFisica + off].rb  = progImage[posLog].rb;
					hw.mem.pos[baseFisica + off].p   = progImage[posLog].p;
				}
			}
		}
	}

}
