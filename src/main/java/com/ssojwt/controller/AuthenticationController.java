package com.ssojwt.controller;

import com.ssojwt.model.json.request.AuthenticationRequest;
import com.ssojwt.model.json.response.AuthenticationResponse;
import com.ssojwt.model.security.SpringUserDetails;
import com.ssojwt.security.TokenUtils;

import javax.servlet.http.HttpServletRequest;

import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.springframework.mobile.device.Device;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("auth")
public class AuthenticationController {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final String tokenHeader = "X-Auth-Token";

    @Autowired
    private TokenUtils tokenUtils;

    @Autowired
    private UserDetailsService userDetailsService;

    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<?> authenticationRequest(
            @RequestBody AuthenticationRequest authenticationRequest,
            HttpServletRequest request) throws AuthenticationException {

        Device device = (Device) request.getAttribute("currentDevice");

        // Perform the authentication

        // Reload password post-authentication so we can generate token
        UserDetails userDetails = this.userDetailsService.loadUserByUsername(authenticationRequest.getUsername());
        String token = this.tokenUtils.generateToken(userDetails, device);

        // Return the token
        return ResponseEntity.ok(new AuthenticationResponse(token));
    }

    @RequestMapping(value = "refresh", method = RequestMethod.GET)
    public ResponseEntity<?> authenticationRequest(HttpServletRequest request)
            throws InvalidJwtException, MalformedClaimException {
        Device device = (Device) request.getAttribute("currentDevice");

        String token = request.getHeader(this.tokenHeader);
        JwtClaims claims = this.tokenUtils.getClaimsFromToken(token);
        String username = this.tokenUtils.getUsernameFromClaims(claims);

        SpringUserDetails springUserDetails = (SpringUserDetails) this.userDetailsService.loadUserByUsername(username);
        if (this.tokenUtils.canTokenBeRefreshed(claims, springUserDetails.getLastPasswordReset())) {
            String refreshedToken = this.tokenUtils.generateToken(springUserDetails, device);
            return ResponseEntity.ok(new AuthenticationResponse(refreshedToken));
        } else {
            return ResponseEntity.badRequest().body(null);
        }
    }

}
