package com.example.serversent;

import com.entity.User;
import com.model.UserViewModel;
import com.repo.UserRepo;
import org.apache.catalina.servlet4preview.http.PushBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;

@SpringBootApplication
@EntityScan("com.entity")
@EnableJpaRepositories("com.repo")
public class ServerSentApplication {
	@Autowired
	private UserRepo userRepo;

	public static void main(String[] args) {
		SpringApplication.run(ServerSentApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner() {
		return(x)->{
			User user = new User();
			user.setUsername("admin");
			user.setPassword("pwd");
			user.setRole("ROLE_ADMIN");
			user.setRealName("syabana");
			user.setEnabled(true);
			userRepo.save(user);

            user = new User();
            user.setUsername("user1");
            user.setPassword("pwd");
            user.setRole("ROLE_USER");
            user.setRealName("tes1");
			user.setEnabled(true);
            userRepo.save(user);

            user = new User();
            user.setUsername("user2");
            user.setPassword("pwd");
            user.setRole("ROLE_USER");
            user.setRealName("tes2");
			user.setEnabled(true);
            userRepo.save(user);
		};
	}

	private class MyUserDetailService implements UserDetailsService {

		@Override
		public UserDetails loadUserByUsername(String s) throws UsernameNotFoundException {
			User user = userRepo.findByUsernameAndEnabled(s,true);
			org.springframework.security.core.userdetails.User user1 = new org.springframework.security.core.userdetails.User(
					user.getUsername(),
					user.getPassword(),
					AuthorityUtils.commaSeparatedStringToAuthorityList(user.getRole())
			);
			return user1;
		}
	}

	@EnableWebSecurity
	public class MySecurity extends WebSecurityConfigurerAdapter {

		@Autowired
		protected void configure(AuthenticationManagerBuilder auth) throws Exception {
			auth
					.userDetailsService(new MyUserDetailService());
		}

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			http
					.authorizeRequests()
					.antMatchers("/user**").hasRole("USER")
					.antMatchers("/admin**").hasRole("ADMIN")
					.and()
					.formLogin().permitAll()
					.and()
					.csrf().disable();
		}
	}

	@Controller
	public class MyController {
		@Autowired
		private ApplicationEventPublisher applicationEventPublisher;

		@Autowired
		private SimpMessagingTemplate template;

		@GetMapping("/user")
		public String showIndex(Model model, Authentication authentication) {
			UserDetails user = (UserDetails) authentication.getPrincipal();
		    String username = user.getUsername();
			model.addAttribute("username",username);
		    return "user";
		}

		@GetMapping("/admin")
		public String showAdmin(Model model) {
            model.addAttribute("users",userRepo.findAll());
		    return "admin";
        }

        @GetMapping("/form")
		public String showForm(UserViewModel userViewModel, @RequestParam(name = "u") String username
								,Model model) {
			User user = userRepo.findOne(username);
			userViewModel.setUsername1(user.getUsername());
			userViewModel.setPassword(user.getPassword());
			userViewModel.setRole(user.getRole());
			userViewModel.setRealName(user.getRealName());
			userViewModel.setEnabled(user.isEnabled()?true:false);
			model.addAttribute(userViewModel);
			return "user_form";
		}
		@GetMapping("/")
		public String showRegForm() {
			return "user_reg";
		}

		@PostMapping("/simpan")
		public String simpan(UserViewModel userViewModel) {
			//UserDetails userDetails = (org.springframework.security.core.userdetails.User) authentication.getPrincipal();
			User user = new User();
			user.setUsername(userViewModel.getUsername1());
			user.setPassword(userViewModel.getPassword());
			user.setRole(userViewModel.getRole());
			user.setRealName(userViewModel.getRealName());
			user.setEnabled(userViewModel.isEnabled());
			userRepo.save(user);
            template.convertAndSendToUser(user.getUsername(),"/queue/message","ok");
			return "redirect:/admin";
		}

		@GetMapping("/thanks")
		public String showThanks() {
			return "thanks";
		}

		@PostMapping("/simpan_reg")
		public String simpanReg(UserViewModel userViewModel) {
			User user = new User();
			user.setUsername(userViewModel.getUsername1());
			user.setPassword(userViewModel.getPassword());
			user.setRole("ROLE_USER");
			user.setRealName(userViewModel.getRealName());
			user.setEnabled(false);
			userRepo.save(user);
			template.convertAndSendToUser("admin","/queue/message","ada permintaan..silahkan tekan F5");
			return "redirect:/thanks";
		}
	}

	@Configuration
	@EnableWebSocketMessageBroker
	public class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {

		@Override
		public void registerStompEndpoints(StompEndpointRegistry stompEndpointRegistry) {
			stompEndpointRegistry.addEndpoint("/stomp").withSockJS();
		}

		@Override
		public void configureMessageBroker(MessageBrokerRegistry registry) {
			registry.enableSimpleBroker("/queue");
			registry.setApplicationDestinationPrefixes("/app");
			registry.setUserDestinationPrefix("/user");
		}
	}

}
