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
			userRepo.save(user);

            user = new User();
            user.setUsername("user1");
            user.setPassword("pwd");
            user.setRole("ROLE_USER");
            user.setRealName("tes1");
            userRepo.save(user);

            user = new User();
            user.setUsername("user2");
            user.setPassword("pwd");
            user.setRole("ROLE_USER");
            user.setRealName("tes2");
            userRepo.save(user);
		};
	}

	private class MyUserDetailService implements UserDetailsService {

		@Override
		public UserDetails loadUserByUsername(String s) throws UsernameNotFoundException {
			User user = userRepo.findOne(s);
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

	public class MyCustomEvent extends ApplicationEvent {

		private String message;

		public MyCustomEvent(Object source,String message) {
			super(source);
			this.message = message;
		}

		public String getMessage() {
			return message;
		}
	}

	@Controller
	public class MyController {
		@Autowired
		private ApplicationEventPublisher applicationEventPublisher;

		@Autowired
		private SimpMessagingTemplate template;

		private String nama=null;

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
			model.addAttribute(userViewModel);
			return "user_form";
		}

		@PostMapping("/simpan")
		public String simpan(UserViewModel userViewModel,Principal principal) {
			//UserDetails userDetails = (org.springframework.security.core.userdetails.User) authentication.getPrincipal();
			User user = new User();
			user.setUsername(userViewModel.getUsername1());
			user.setPassword(userViewModel.getPassword());
			user.setRole(userViewModel.getRole());
			user.setRealName(userViewModel.getRealName());
			userRepo.save(user);
            nama = user.getUsername();
            template.convertAndSendToUser(principal.getName(),"/queue/message","ok");
			return "redirect:/admin";
		}


		@GetMapping("/check")
		public ResponseBodyEmitter responseBodyEmitter(Authentication authentication) {
            UserDetails user = (UserDetails) authentication.getPrincipal();
            String username = user.getUsername();
		    SseEmitter emitter = new SseEmitter();
		    if(nama!=null && nama.equals(username) ) {
                try {
                    emitter.send(nama + " berhasil diedit",MediaType.TEXT_PLAIN);
                    nama=null;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            emitter.complete();
		    return emitter;
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
			registry.enableSimpleBroker("/topic","/queue");
			//registry.setApplicationDestinationPrefixes("/app");
			//registry.setUserDestinationPrefix("/user");
		}
	}

}
