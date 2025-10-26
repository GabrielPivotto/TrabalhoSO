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

import java.util.Scanner;
import java.util.Queue;
import java.util.List;
import java.net.SocketTimeoutException;
import java.util.LinkedList;

public class Sistema {

    // -------------------------------------------------------------------------------------------------------
    // --------------------- H A R D W A R E - definicoes de HW
    // ----------------------------------------------
    // -------------------------------------------------------------------------------------------------------
    // --------------------- M E M O R I A - definicoes de palavra de memoria,
    // memória ----------------------
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
            p = _p;
        }
    }

    // -------------------------------------------------------------------------------------------------------
    // --------------------- C P U - definicoes da CPU
    // -----------------------------------------------------
    public enum Opcode {
        DATA, ___, // se memoria nesta posicao tem um dado, usa DATA, se nao usada ee NULO ___
        JMP, JMPI, JMPIG, JMPIL, JMPIE, // desvios
        JMPIM, JMPIGM, JMPILM, JMPIEM,
        JMPIGK, JMPILK, JMPIEK, JMPIGT,
        ADDI, SUBI, ADD, SUB, MULT, // matematicos
        LDI, LDD, STD, LDX, STX, MOVE, // movimentacao
        SYSCALL, STOP                  // chamada de sistema e parada
    }

    public enum Interrupts {           // possiveis interrupcoes que esta CPU gera
        noInterrupt, intEnderecoInvalido, intInstrucaoInvalida, intOverflow;
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

        public CPU(Memory _mem, boolean _debug) { // ref a MEMORIA passada na criacao da CPU
            maxInt = 32767;            // capacidade de representacao modelada
            minInt = -32767;           // se exceder deve gerar interrupcao de overflow
            m = _mem.pos;              // usa o atributo 'm' para acessar a memoria, só para ficar mais pratico
            reg = new int[10];         // aloca o espaço dos registradores - regs 8 e 9 usados somente para IO

            debug = _debug;            // se true, print da instrucao em execucao

        }

        public void setAddressOfHandlers(InterruptHandling _ih, SysCallHandling _sysCall) {
            ih = _ih;                  // aponta para rotinas de tratamento de int
            sysCall = _sysCall;        // aponta para rotinas de tratamento de chamadas de sistema
        }

        public void setUtilities(Utilities _u) {
            u = _u;                     // aponta para rotinas utilitárias - fazer dump da memória na tela
        }

        public void setDebug(boolean value) {debug = value;}

        // verificação de enderecamento 
        private boolean legal(int e, int[] tabPag) { // todo acesso a memoria tem que ser verificado se é válido - 
            // aqui no caso se o endereco é um endereco valido em toda memoria
            System.out.println(e);
            if (e >= 0 && e < tabPag.length * so.tamFrame) { //se "e" nao for maior que a qtd total de linhas do programa
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

        /*public void setContext(int id) {                 // usado para setar o contexto da cpu para rodar um processo
            // [ nesta versao é somente colocar o PC na posicao 0 ]

            PCB pcb = so.getProcesso(id);
            pc = pcb.pcState;                                     // pc cfe endereco logico
            reg = pcb.regState;
            irpt = Interrupts.noInterrupt;                // reset da interrupcao registrada
        }*/

        public int run(int id, int instMax) {                               // execucao da CPU supoe que o contexto da CPU, vide acima, 
            // esta devidamente setado
            PCB pcb = so.getProcesso(id);
            System.out.println(pcb);
            pc = pcb.pcState;                                     // pc cfe endereco logico
            reg = pcb.regState;
            irpt = Interrupts.noInterrupt;                // reset da interrupcao registrada

            int[] tabPag = pcb.tabelaPag;
            int tFrame = so.tamFrame;
            so.atualizaPtrProcess(id); // atualiza o ptr que diz qual processo esta rodando
            // (aponta pra pos na lista de processos do S.O.)

            cpuStop = false;

			int instCount = 0;
            while (!cpuStop) {      // ciclo de instrucoes. acaba cfe resultado da exec da instrucao, veja cada caso.
                // --------------------------------------------------------------------------------------------------
				// FASE DE FETCH
                int pagAtual = pc / tFrame;   // pc/so.tamFrame -> pagina atual
                int linhaAtual = pc % tFrame; // pc%so.tamFrame -> deslocamento na pagina

                System.out.println("pagina atual = " + pc / tFrame);
                System.out.println("linha atual = " + pc % tFrame);
                System.out.println("PC = " + pc);
                System.out.println("Endereco traduzido = " + (tabPag[pagAtual] * tFrame + linhaAtual));

                if (legal(pc, tabPag)) { // pc valido
                    ir = m[tabPag[pagAtual] * tFrame + linhaAtual];  // <<<<<<<<<<<< AQUI faz FETCH - busca posicao da memoria apontada por pc, guarda em ir
                    // resto é dump de debug

                    if (debug) {
                        System.out.print(" regs: ");
                        for (int i = 0; i < 10; i++) {
                            System.out.print(" r[" + i + "]:" + reg[i]);
                        }
                        ;
                        System.out.println();
                    }
                    if (debug) {
                        System.out.print("pc: " + pc + "       exec: ");
                        u.dump(ir);
                    }

                    // --------------------------------------------------------------------------------------------------
                    // FASE DE EXECUCAO DA INSTRUCAO CARREGADA NO ir
                    switch (ir.opc) {       // conforme o opcode (código de operação) executa

                        // Instrucoes de Busca e Armazenamento em Memoria
                        case LDI: // Rd ← k        veja a tabela de instrucoes do HW simulado para entender a semantica da instrucao
                            reg[ir.ra] = ir.p;
                            pc++;
                            break;
                        case LDD: // Rd <- [A]
                            if (legal(ir.p, tabPag)) {
                                int pag = ir.p / tFrame;
                                int deslocamento = ir.p % tFrame;

                                reg[ir.ra] = m[tabPag[pag] * tFrame + deslocamento].p;
                                pc++;
                            }
                            break;
                        case LDX: // RD <- [RS] // NOVA
                            if (legal(reg[ir.rb], tabPag)) {
                                int pag = reg[ir.rb] / tFrame;
                                int deslocamento = reg[ir.rb] % tFrame;

                                reg[ir.ra] = m[tabPag[pag] * tFrame + deslocamento].p;
                                pc++;
                            }
                            break;
                        case STD: // [A] ← Rs
                            if (legal(ir.p, tabPag)) {
                                int pag = ir.p / tFrame;
                                int deslocamento = ir.p % tFrame;

                                m[tabPag[pag] * tFrame + deslocamento].opc = Opcode.DATA;
                                m[tabPag[pag] * tFrame + deslocamento].p = reg[ir.ra];
                                pc++;
                                if (debug) {
                                    System.out.print("                                  ");
                                    u.dump(ir.p, ir.p + 1);
                                }
                            }
                            break;
                        case STX: // [Rd] ←Rs
                            if (legal(reg[ir.ra], tabPag)) {
                                int pag = reg[ir.ra] / tFrame;
                                int deslocamento = reg[ir.ra] % tFrame;

                                m[tabPag[pag] * tFrame + deslocamento].opc = Opcode.DATA;
								m[tabPag[pag] * tFrame + deslocamento].p = reg[ir.rb];
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
							if(legal(ir.p, tabPag)) {pc = ir.p;}
                            break;
                        case JMPIM: // PC <- [A]
							if(legal(m[ir.p].p, tabPag)){
                            	pc = m[ir.p].p;
							}	
                            break;
                        case JMPIG: // If Rc > 0 Then PC ← Rs Else PC ← PC +1
                            if (legal(ir.ra, tabPag) && reg[ir.rb] > 0) {
                                pc = reg[ir.ra];
                            } else {
                                pc++;
                            }
                            break;
                        case JMPIGK: // If RC > 0 then PC <- k else PC++
                            if (legal(ir.p, tabPag) && reg[ir.rb] > 0) {
                                pc = ir.p;
                            } else {
                                pc++;
                            }
                            break;
                        case JMPILK: // If RC < 0 then PC <- k else PC++
                            if (legal(ir.p, tabPag) && reg[ir.rb] < 0) {
                                pc = ir.p;
                            } else {
                                pc++;
                            }
                            break;
                        case JMPIEK: // If RC = 0 then PC <- k else PC++
                            if (legal(ir.p, tabPag) &&reg[ir.rb] == 0) {
                                pc = ir.p;
                            } else {
                                pc++;
                            }
                            break;
                        case JMPIL: // if Rc < 0 then PC <- Rs Else PC <- PC +1
                            if (legal(ir.ra, tabPag) && reg[ir.rb] < 0) {
                                pc = reg[ir.ra];
                            } else {
                                pc++;
                            }
                            break;
                        case JMPIE: // If Rc = 0 Then PC <- Rs Else PC <- PC +1
                            if (legal(ir.ra, tabPag) && reg[ir.rb] == 0) {
                                pc = reg[ir.ra];
                            } else {
                                pc++;
                            }
                            break;
                        case JMPIGM: // If RC > 0 then PC <- [A] else PC++
                            if (legal(ir.p, tabPag)) {
                                if (reg[ir.rb] > 0) {
                                    pc = m[ir.p].p;
                                } else {
                                    pc++;
                                }
                            }
                            break;
                        case JMPILM: // If RC < 0 then PC <- k else PC++
                            if (legal(m[ir.p].p, tabPag) && reg[ir.rb] < 0) {
                                pc = m[ir.p].p;
                            } else {
                                pc++;
                            }
                            break;
                        case JMPIEM: // If RC = 0 then PC <- k else PC++
                            if (legal(m[ir.p].p, tabPag) && reg[ir.rb] == 0) {
                                pc = m[ir.p].p;
                            } else {
                                pc++;
                            }
                            break;
                        case JMPIGT: // If RS>RC then PC <- k else PC++
                            if (legal(ir.p, tabPag) && reg[ir.ra] > reg[ir.rb]) {
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
                            return 2;

                        case STOP: // por enquanto, para execucao
                            sysCall.stop();
                            cpuStop = true;
                            so.gerenteProg.desalocaProcesso(pcb.id);
                            return 0;

                        // Inexistente
                        default:
                            irpt = Interrupts.intInstrucaoInvalida;
                            break;
                    }
                }
                // --------------------------------------------------------------------------------------------------
                // VERIFICA INTERRUPÇÃO !!! - TERCEIRA FASE DO CICLO DE INSTRUÇÕES
                if (irpt != Interrupts.noInterrupt) { // existe interrupção
                    ih.handle(irpt);                  // desvia para rotina de tratamento - esta rotina é do SO
                    cpuStop = true;                   // nesta versao, para a CPU
                }
				instCount++;
                System.out.println(instCount + "---" + instMax);
				if(instCount==instMax) {
					cpuStop = true;
                    break;
				}
            } // FIM DO CICLO DE UMA INSTRUÇÃO
			pcb.pcState = pc;
			pcb.regState = reg;
            System.out.println(pcb);
            //so.gerenteProg.desalocaProcesso(so.gerenteProg.novoIdProcesso); //inserido aqui por finalidade de teste da funcao REMOVER DEPOIS
            return 1;
        }


    }
    // ------------------ C P U - fim
    // -----------------------------------------------------------------------
    // ------------------------------------------------------------------------------------------------------

    // ------------------- HW - constituido de CPU e MEMORIA
    // -----------------------------------------------
    public class HW {

        public Memory mem;
        public CPU cpu;

        public HW(int tamMem) {
            mem = new Memory(tamMem);
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

        private HW hw; // referencia ao hw se tiver que setar algo

        public InterruptHandling(HW _hw) {
            hw = _hw;
        }

        public void handle(Interrupts irpt) {
            // apenas avisa - todas interrupcoes neste momento finalizam o programa
            System.out.println(
                    "                                               Interrupcao " + irpt + "   pc: " + hw.cpu.pc);
        }
    }

    // ------------------- C H A M A D A S D E S I S T E M A - rotinas de tratamento
    // ----------------------
    public class SysCallHandling {

        private HW hw; // referencia ao hw se tiver que setar algo

        public SysCallHandling(HW _hw) {
            hw = _hw;
        }

        public void stop() { // chamada de sistema indicando final de programa
            // nesta versao cpu simplesmente pára
            System.out.println("                                               SYSCALL STOP");
        }

        public void handle() { // chamada de sistema 
            // suporta somente IO, com parametros 
            // reg[8] = in ou out    e reg[9] endereco do inteiro
            System.out.println("SYSCALL pars:  " + hw.cpu.reg[8] + " / " + hw.cpu.reg[9]);

            if (hw.cpu.reg[8] == 1) {
                Scanner in = new Scanner(System.in);
                System.out.print("IN:    ");
                int valor = in.nextInt();
                hw.mem.pos[hw.cpu.reg[9]].opc = Opcode.DATA;
                hw.mem.pos[hw.cpu.reg[9]].p = valor;
                in.close();

            } else if (hw.cpu.reg[8] == 2) {
                // escrita - escreve o conteuodo da memoria na posicao dada em reg[9]
                System.out.println("OUT:   " + hw.mem.pos[hw.cpu.reg[9]].p);
            } else {
                System.out.println("  PARAMETRO INVALIDO");
            }
        }
    }

    // ------------------ U T I L I T A R I O S D O S I S T E M A
    // -----------------------------------------
    // ------------------ load é invocado a partir de requisição do usuário
    // carga na memória
    public class Utilities {

        private HW hw;
        private int contProg;

        public Utilities(HW _hw) {
            hw = _hw;
            contProg = 0;
        }

        private int loadProgram(Word[] p) {
            if (so.gerenteProg.criaProcesso(p)) {
                Word[] m = hw.mem.pos;
                int contLinha = 0;

                int[] tabPag = so.getProcesso(so.gerenteProg.novoIdProcesso).tabelaPag; // pega a tabPag do pcb na lista de processos do SO
                for (int i = 0; i < tabPag.length; i++) { // para cada pagina
                    for (int j = 0; j < so.tamFrame; j++) { // para cada linha que cabe em uma pagina

                        // pega o frame apontado pela tabPag (tabPag[i]) e vai incrementando linha por linha (+j)
                        // alocando as instrucoes do programa na memoria
                        m[tabPag[i] * so.tamFrame + j].opc = p[contLinha].opc;
                        m[tabPag[i] * so.tamFrame + j].ra = p[contLinha].ra;
                        m[tabPag[i] * so.tamFrame + j].rb = p[contLinha].rb;
                        m[tabPag[i] * so.tamFrame + j].p = p[contLinha].p;

                        contLinha++;

                        if (contLinha >= p.length) {
                            break;
                        } // se um processo nao usar uma pagina inteira
                    }
                }
                return so.gerenteProg.novoIdProcesso;
            }
            return -1;
        }

        // dump da memória
        public void dump(Word w) { // funcoes de DUMP nao existem em hardware - colocadas aqui para facilidade
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
            loadProgram(p); // carga do programa na memoria
            System.out.println("---------------------------------- programa carregado na memoria");
            dump(0, p.length); // dump da memoria nestas posicoes
            //hw.cpu.setContext(id); // seta pc para endereço 0 - ponto de entrada dos programas <<<< ANOTACAO: mudar o context aqui faz diferenca se pc sempre eh traduzido para mem real?
            System.out.println("---------------------------------- inicia execucao ");
            hw.cpu.run(so.gerenteProg.novoIdProcesso, 0); // cpu roda programa ate parar
            System.out.println("---------------------------------- memoria após execucao ");
            dump(0, p.length); // dump da memoria com resultado
        }


        private void execAll(){
            Queue<Integer> ready = new LinkedList<Integer>();
            Queue<Integer> blocked = new LinkedList<Integer>();

            //======Comando para deixar a concorrencia mais aparente======//
            try {
                Thread.sleep(10000); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            //============================================================//

            for (PCB pcb : so.listaDeProcessos) {
                if (pcb!=null) ready.add(pcb.id);
            }

            if(ready.size() == 0) {
                System.out.println("Nao ha processos para executar");
                return;
            }

            int currID = -1;
            while(!ready.isEmpty()){
                System.out.println(ready.toString());
                currID = ready.remove();
                switch (hw.cpu.run(currID, 2)) {
                    case 1:
                        ready.add(currID);
                        break;
                    
                    case 2:
                        blocked.add(currID);
                    default:
                        break;
                }
            }
        }
    }

    public class PCB { //Process Control Block

        public int id;
        public int[] tabelaPag;
        public int[] regState;
        public int pcState;

        public PCB(int _id, int[] _tabelaPag) {

            id = _id;
            tabelaPag = _tabelaPag;
            regState = new int[10];
            pcState = 0;
        }

        public String toString(){
            String s = "ID: " + id +
            "\nEstado dos registradores: ";
            for (int n : regState) {
                s = s + n+ ", ";
            }
            s = s + "\nEstado do PC: " + pcState +
            "\nPáginas Alocadas: ";
            for (int i : tabelaPag) {
                s = s + i + ", ";
            }
            return s;

        }

    }

    public class GM {

        boolean aloca(int nroPalavras, int[] tabelaPag) { // determina os frames que os quadros serao alocados
            if (((nroPalavras + so.tamFrame - 1) / so.tamFrame) > so.qtdFramesDisp) {
                return false;
            } // se nao tem frame suficiente, nao aloca

            int qtdPag = so.posOcupadas.length;
            int contAux = 0;

            for (int i = 0; i < qtdPag; i++) { // "i" representa a primeira linha do frame atual
                if (!so.posOcupadas[i]) { // se o frame nao esta ocupado, coloca o numero do frame alocado (i/so.tamFrame)
                    tabelaPag[contAux] = i;
                    so.posOcupadas[i] = true; //ocupa pos na memoria

                    contAux++;
                    so.qtdFramesDisp--;

                    if (contAux >= tabelaPag.length) {
                        break;
                    }
                }
            }

            return true;
        }

        void desaloca(int[] tabelaPag) {
            for (int i : tabelaPag) {
                so.posOcupadas[i] = false;
            }
        }
    }

    public class GP {

        public int novoIdProcesso = 0;

        boolean criaProcesso(Word[] programa) {
            if(programa == null) return false;
            novoIdProcesso++;
            System.out.println("GP id = " + novoIdProcesso);
            int qtdPag = (programa.length + so.tamFrame - 1) / so.tamFrame; // formula para arredondamento para cima (pois 1.2 paginas tem que arredondar para 2)
            PCB pcb = new PCB(novoIdProcesso, new int[qtdPag]); // gera PCB para o processo

            if (so.gerenteMem.aloca(programa.length, pcb.tabelaPag)) { // se for possivel alocar em memoria
                so.addListProcessos(pcb);

                return true;
            }

            novoIdProcesso--;

            return false;
        }

        boolean desalocaProcesso(int id) {
            for (PCB pcb : so.listaDeProcessos) { 			// procura por todos os pcb
				if(pcb != null && pcb.id == id) {			// se achou...
                    so.gerenteMem.desaloca(pcb.tabelaPag); 	// ...tira da memoria...
                    so.removeListProcesso(id); 				// ...e tira da lista de processos do SO

                    return true;
                }
            }

            return false;
        }
    }

    public class SO {

        public InterruptHandling ih;
        public SysCallHandling sc;
        public Utilities utils;
        public int tamFrame;
        public GM gerenteMem;
        public GP gerenteProg;
        public PCB[] listaDeProcessos;	// guarda os processos (PCB) que estao em memoria (pode ser considerado a lista de ready)
        public int ptrProcessRunning;	// vai guardar a pos na lista de PCB do processo que esta rodando
        public int qtdFramesDisp;		// guarda qtd de frames disponiveis para rapidamente checar se um processo cabe
        public int contProcessos; 		// conta a qtd de processos
        public boolean[] posOcupadas; 	// guarda os frames disponiveis (false) e ocupados (true)
        public ThreadMenu tMenu; 
        public ThreadExecAll tExecAll;

        public SO(HW hw, int _tamFrame) {
            ih = new InterruptHandling(hw); // rotinas de tratamento de int
            sc = new SysCallHandling(hw); // chamadas de sistema
            hw.cpu.setAddressOfHandlers(ih, sc);
            utils = new Utilities(hw);

            tamFrame = _tamFrame;
            gerenteMem = new GM();
            gerenteProg = new GP();
            qtdFramesDisp = hw.mem.pos.length / tamFrame;
            listaDeProcessos = new PCB[qtdFramesDisp]; // o maximo de processos seria a qtd de paginas na memoria
            contProcessos = 0;
            posOcupadas = new boolean[qtdFramesDisp];
            ptrProcessRunning = -1;

            tMenu = new ThreadMenu();
            tExecAll = new ThreadExecAll();
        }

        private void atualizaPtrProcess(int id) {
            for (int i = 0; i < listaDeProcessos.length - 1; i++) {
                if (listaDeProcessos[i] != null && listaDeProcessos[i].id == id) {
                    ptrProcessRunning = i;

                    break;
                }
            }
        }

        private boolean addListProcessos(PCB pcb) {
            if (listaDeProcessos.length - 1 == contProcessos) {
                return false;
            } // se lista da cheia

            for (int i = 0; i < listaDeProcessos.length - 1; i++) {
                if (listaDeProcessos[i] == null) {
                    listaDeProcessos[i] = pcb;
                    contProcessos++;

                    return true;
                }
            }

            return false;
        }

        private PCB getProcesso(int id) {
            for(PCB pcb : listaDeProcessos) {
                if(pcb != null) {
                    //System.out.println("PCB " + pcb.id);
                    if(pcb.id == id) {
                    	return pcb;
                    }
                }
            }

            return null;
        }

        private boolean removeListProcesso(int id) {
            for(int i = 0; i < listaDeProcessos.length - 1; i++) {
                if(listaDeProcessos[i] != null && listaDeProcessos[i].id == id) {
                	listaDeProcessos[i] = null;
                    contProcessos--;
                    return true;
                }
            }

            return false;
        } 
    }
    // -------------------------------------------------------------------------------------------------------
    // ------------------- S I S T E M A
    // --------------------------------------------------------------------

    public HW hw;
    public SO so;
    public Programs progs;

    public Sistema(int tamMem, int tamFrame) {
        hw = new HW(tamMem);           // memoria do HW tem tamMem palavras
        so = new SO(hw, tamFrame);
        hw.cpu.setUtilities(so.utils); // permite cpu fazer dump de memoria ao avancar
        progs = new Programs();
    }

    public void run() {
        so.tMenu.start();

        try {
                so.tMenu.join();
        } catch (InterruptedException e) {
            System.err.println("A thread principal foi interrompida enquanto esperava.");
            Thread.currentThread().interrupt();  // Restaura o estado de interrupção
        }
	}
        // so.utils.loadAndExec(progs.retrieveProgram("fatorial"));
        // fibonacci10,
        // fibonacci10v2,
        // progMinimo,
        // fatorialWRITE, // saida
        // fibonacciREAD, // entrada
        // PB
        // PC, // bubble sort
    // ------------------- S I S T E M A - fim
    // --------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------

    // -------------------------------------------------------------------------------------------------------
    // ------------------- instancia e testa sistema
    public static void main(String args[]) {
        Sistema s = new Sistema(1024, 8);
        s.run();
    }

    public class ThreadExecAll extends Thread {
        
        @Override
        public void run() {
            so.utils.execAll();
        }
    }

    public class ThreadMenu extends Thread {
        
        @Override
        public void run() {
            boolean exit = false;
            boolean execAll = false;

            String help = "new <nomeDePrograma> - cria um processo na memória. Pede ao GM para alocar memória. Cria PCB, seta partição\n" + 
                                                 "ou tabela de páginas do processo no PCB, etc. coloca processo em uma lista de processos\n" +
                                                 "(prontos). Esta chamada retorna um identificador único do processo no sistema (ex.: 1, 2, 3, …)\n" +
                          "rm <id> - retira o processo id do sistema, tenha ele executado ou não\n" +
                          "ps - lista todos processos existentes\n" +
                          "dump <id> - lista o conteúdo do PCB e o conteúdo da memória do processo com id\n" +
                          "dumpM <inicio, fim> - lista a memória entre posições início e fim, independente do processo\n" +
                          "exec <id> - executa o processo com id fornecido. se não houver processo, retorna erro.\n" + 
                          "traceOn - liga modo de execução em que CPU print cada instrução executada\n" +
                          "traceOff - desliga o modo acima\n" +
                          "execAll - executa todos os processos prontos\n" +
                          "help - lista as instruções\n" +
                          "exit - sai do sistema";

            System.out.println(help);
            Scanner in = new Scanner(System.in);

            while (!exit){
            	String[] input = {""};

		    	if(in.hasNextLine()) input = in.nextLine().split(" ");

		    	switch(input[0]){
                    case "new":
                    	if(input.length == 1) {
                    		System.out.println("Programa não declarado");

                    	    break;
                    	}

                    	int program = so.utils.loadProgram(progs.retrieveProgram(input[1]));
                    
		    			if(program == -1) {System.out.println("Programa nao encontrado.");}
                    	else {System.out.println("Programa carregado com ID "+ program);}

                    	break;

                    case "rm":
                        if(input.length == 1) {
                            System.out.println("Processo não declarado");

                            break;
                        }

                        boolean found = so.gerenteProg.desalocaProcesso(Integer.parseInt(input[1]));

		    			if(!found) {System.out.println("Processo nao encontrado.");}
                        else {System.out.println("Processo descarregado.");}

                        break;

                    case "ps":
                        PCB[] processos = so.listaDeProcessos;
                        for(PCB pcb : processos) {if(pcb != null) {System.out.println(pcb.id);}}

		    			break;

                    case "dump":
                        if (input.length == 1) {
                            System.out.println("Processo não declarado.");
                            break;
                        }

                        PCB process = so.getProcesso(Integer.parseInt(input[1]));

                        System.out.println(process);
                        System.out.println("Memória do processo:");

                        for(int pag : process.tabelaPag) {so.utils.dump(so.tamFrame*pag, (so.tamFrame*(pag+1))-1);}

		    			break;
                    
                    case "dumpM":
                        if(input.length<3){
                            System.out.println("Endereços não declarados.");

                            break;
                        }

                        so.utils.dump(Integer.parseInt(input[1]),Integer.parseInt(input[2]));

		    			break;

                    case "exec":
                        if(execAll) {
                            System.out.println("Comando execAll em andamento, somente um comando de execucao por vez");
                        
                            break;
                        }
                        if(input.length == 1) {
                            System.out.println("Processo não declarado.");
                        
                            break;
                        }

                        if(!input[1].matches("[0-9]+")) { // [0-9]+ -> a string deve conter apenas numeros de 0 ate 9 somente 
                            System.out.println("Use apenas o id do processo para identifica-lo");

                            break;
                        } 

                        int procID = Integer.parseInt(input[1]);

                        if(so.getProcesso(procID) == null) {
                            System.out.println("Processo nao encontrado");

                            break;
                        }

                        if(hw.cpu.run(procID, 0)){System.out.println("Processo " +procID + " concluído.");}
                        else {System.out.println("não rodou :(");}

                        so.gerenteProg.desalocaProcesso(procID);

                        break;

                    case "traceOn":
                        hw.cpu.setDebug(true);
                        System.out.println("Trace ativado");

                        break;

                    case "traceOff":
                        hw.cpu.setDebug(false);
                        System.out.println("Trace desligado");

                        break;

                    case "help":
                        System.out.println(help);

                        break;

                    case "exit":
                        exit = true;
                        in.close();
                    
                        break;

                    case "execAll":
                        execAll = true;

                        if(so.tExecAll != null && so.tExecAll.isAlive()) { // se tExecAll ainda esta rodando a funcao
                            System.out.println("tExecAll já está em execução. Aguarde a conclusão...");
                        } else {
                            so.tExecAll = new ThreadExecAll(); // se acabou, cria nova instancia
                                                               // (aparentemente nao eh possivel utilizar uma mesma thread)
                            so.tExecAll.start(); //inicia nova execucao
                        }

                        execAll = false;

                        break;

                    default:
                        System.out.println("Comando inválido, digite 'help' para listar os comandos.");
                }
            }
        }
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
                if (p != null & p.name.equals(pname)) {
                    return p.image;
                }
            }
            return null;
        }

        public Program[] progs = {
            new Program("fatorial",
            new Word[]{
                // este fatorial so aceita valores positivos. nao pode ser zero
                // linha coment
                new Word(Opcode.LDI, 0, -1, 7), // 0 r0 é valor a calcular fatorial
                new Word(Opcode.LDI, 1, -1, 1), // 1 r1 é 1 para multiplicar (por r0)
                new Word(Opcode.LDI, 6, -1, 1), // 2 r6 é 1 o decremento
                new Word(Opcode.LDI, 7, -1, 8), // 3 r7 tem posicao 8 para fim do programa
                new Word(Opcode.JMPIE, 7, 0, 0), // 4 se r0=0 pula para r7(=8)
                new Word(Opcode.MULT, 1, 0, -1), // 5 r1 = r1 * r0 (r1 acumula o produto por cada termo)
                new Word(Opcode.SUB, 0, 6, -1), // 6 r0 = r0 - r6 (r6=1) decrementa r0 para proximo termo
                new Word(Opcode.JMP, -1, -1, 4), // 7 vai p posicao 4
                new Word(Opcode.STD, 1, -1, 10), // 8 coloca valor de r1 na posição 10
                new Word(Opcode.STOP, -1, -1, -1), // 9 stop
                new Word(Opcode.DATA, -1, -1, -1) // 10 ao final o valor está na posição 10 da memória
            }),
            new Program("fatorialV2",
            new Word[]{
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
                new Word(Opcode.DATA, -1, -1, -1)} // POS 19
            ),
            new Program("progMinimo",
            new Word[]{
                new Word(Opcode.LDI, 0, -1, 7),
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
                new Word[]{ // mesmo que prog exemplo, so que usa r0 no lugar de r8
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
            new Word[]{ // mesmo que prog exemplo, so que usa r0 no lugar de r8
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
            new Word[]{
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
                new Word(Opcode.STD, 1, -1, 41), // 25 posicao de memoria onde inicia a serie de fibonacci gerada
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
            new Word[]{
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
                new Word(Opcode.DATA, -1, -1, -1), // POS 15
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
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1)
            }),
            new Program("PC",
            new Word[]{
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
                new Word(Opcode.JMPIEM, -1, 6, 97), // CHAVE 3 para pular quando r7 for 1 e r6 0 para interomper o loop de vez do programa
                new Word(Opcode.LDX, 0, 5, -1), // r0 e ra pegando valores das posições da memoria POS 26
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
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1),
                new Word(Opcode.DATA, -1, -1, -1)
            })
        };
    }
}
