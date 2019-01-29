package br.com.victorpfranca.mybudget.accesscontroll.api;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(of="email")
public class UserDTO {

    private String email;
    private String nome;
    private String sobrenome;
    private Date dataCadastro;
    private Date dataUltimoAcesso;

}
