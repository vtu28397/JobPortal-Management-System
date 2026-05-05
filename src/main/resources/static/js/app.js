document.addEventListener("DOMContentLoaded", () => {
  const input = document.querySelector("[data-table-filter]");
  const rows = document.querySelectorAll("[data-filter-row]");

  if (input && rows.length) {
    input.addEventListener("input", () => {
      const query = input.value.trim().toLowerCase();
      rows.forEach((row) => {
        row.style.display = row.textContent.toLowerCase().includes(query) ? "" : "none";
      });
    });
  }

  document.querySelectorAll("[data-confirm]").forEach((button) => {
    button.addEventListener("click", (event) => {
      if (!window.confirm(button.dataset.confirm)) {
        event.preventDefault();
      }
    });
  });

  const passwordInput = document.querySelector("[data-password-input]");
  const passwordToggle = document.querySelector("[data-password-toggle]");

  if (passwordInput && passwordToggle) {
    passwordToggle.addEventListener("click", () => {
      const isHidden = passwordInput.type === "password";
      passwordInput.type = isHidden ? "text" : "password";
      passwordToggle.textContent = isHidden ? "Hide" : "Show";
    });
  }

  const configureOtpFlow = ({
    phoneSelector,
    otpSelector,
    sendSelector,
    verifySelector,
    statusSelector,
    tokenSelector,
    verifiedPhoneSelector,
    submitSelector,
    initialMessage,
    beforeSend,
  }) => {
    const phoneInput = document.querySelector(phoneSelector);
    const otpInput = document.querySelector(otpSelector);
    const sendButton = document.querySelector(sendSelector);
    const verifyButton = document.querySelector(verifySelector);
    const status = document.querySelector(statusSelector);
    const tokenInput = document.querySelector(tokenSelector);
    const verifiedPhoneInput = document.querySelector(verifiedPhoneSelector);
    const submitButton = document.querySelector(submitSelector);

    if (!phoneInput || !otpInput || !sendButton || !verifyButton || !status || !tokenInput || !verifiedPhoneInput) {
      return;
    }

    const setOtpStatus = (message, state) => {
      status.textContent = message;
      status.dataset.state = state;
    };

    const resetOtpVerification = () => {
      tokenInput.value = "";
      verifiedPhoneInput.value = "";
      if (submitButton) {
        submitButton.disabled = true;
      }
      setOtpStatus(initialMessage, "pending");
    };

    const identifier = () => {
      const digits = phoneInput.value.replace(/\D/g, "");
      return digits.length === 10 ? `91${digits}` : "";
    };

    let otpReqId = "";
    resetOtpVerification();
    phoneInput.addEventListener("input", resetOtpVerification);

    sendButton.addEventListener("click", async () => {
      const mobile = identifier();
      if (!mobile) {
        setOtpStatus("Enter a valid 10 digit mobile number.", "error");
        return;
      }
      if (beforeSend) {
        sendButton.disabled = true;
        setOtpStatus("Checking registered mobile number...", "pending");
        try {
          const result = await beforeSend(mobile, phoneInput.value);
          if (!result.ok) {
            sendButton.disabled = false;
            setOtpStatus(result.message, "error");
            return;
          }
        } catch (error) {
          sendButton.disabled = false;
          setOtpStatus("Could not check this mobile number. Please try again.", "error");
          return;
        }
      }
      if (typeof window.sendOtp !== "function") {
        sendButton.disabled = false;
        setOtpStatus("OTP service is still loading. Try again in a moment.", "error");
        return;
      }
      sendButton.disabled = true;
      setOtpStatus("Sending OTP...", "pending");
      window.sendOtp(
        mobile,
        (data) => {
          otpReqId = data && (data.reqId || data.requestId || data.request_id || data.message || "");
          sendButton.disabled = false;
          setOtpStatus("OTP sent. Enter the code and verify.", "pending");
        },
        () => {
          sendButton.disabled = false;
          setOtpStatus("Could not send OTP. Please try again.", "error");
        }
      );
    });

    verifyButton.addEventListener("click", () => {
      const mobile = identifier();
      const otp = otpInput.value.trim();
      if (!mobile || otp.length < 4) {
        setOtpStatus("Enter your mobile number and OTP.", "error");
        return;
      }
      if (typeof window.verifyOtp !== "function") {
        setOtpStatus("OTP service is still loading. Try again in a moment.", "error");
        return;
      }
      verifyButton.disabled = true;
      setOtpStatus("Verifying OTP...", "pending");
      window.verifyOtp(
        otp,
        (data) => {
          verifyButton.disabled = false;
          window.msg91LatestOtpResponse = data;
          console.log("MSG91 OTP verified:", data);
          const token = extractOtpAccessToken(data) || extractOtpAccessToken(window.msg91LatestOtpResponse);
          if (!token) {
            setOtpStatus("OTP verified, but MSG91 did not return the secure token. Click Verify once more.", "error");
            return;
          }
          tokenInput.value = token;
          verifiedPhoneInput.value = mobile;
          if (submitButton) {
            submitButton.disabled = false;
          }
          setOtpStatus("Mobile number verified.", "success");
        },
        () => {
          verifyButton.disabled = false;
          tokenInput.value = "";
          verifiedPhoneInput.value = "";
          if (submitButton) {
            submitButton.disabled = true;
          }
          setOtpStatus("Invalid OTP. Please try again.", "error");
        }
        ,
        otpReqId || undefined
      );
    });
  };

  const extractOtpAccessToken = (data) => {
    if (!data) {
      return "";
    }
    if (typeof data === "string") {
      return data;
    }
    return data["access-token"]
      || data.accessToken
      || data.access_token
      || data.token
      || (data.message && typeof data.message === "object" && (data.message["access-token"] || data.message.accessToken || data.message.access_token))
      || (typeof data.message === "string" && looksLikeAccessToken(data.message) ? data.message : "")
      || "";
  };

  const looksLikeAccessToken = (value) => {
    const token = value.trim();
    return token.includes(".") || token.length > 80;
  };

  configureOtpFlow({
    phoneSelector: "[data-register-phone]",
    otpSelector: "[data-register-otp]",
    sendSelector: "[data-send-register-otp]",
    verifySelector: "[data-verify-register-otp]",
    statusSelector: "[data-register-otp-status]",
    tokenSelector: "[data-register-otp-token]",
    verifiedPhoneSelector: "[data-register-otp-phone]",
    submitSelector: ".register-submit",
    initialMessage: "Mobile verification is required to register.",
  });

  const otpSwitch = document.querySelector("[data-toggle-login-otp]");
  const otpLoginPanel = document.querySelector("[data-login-otp-panel]");
  const passwordLoginPanel = document.querySelector("[data-password-login-panel]");

  if (otpSwitch && otpLoginPanel) {
    otpSwitch.addEventListener("click", () => {
      otpLoginPanel.hidden = !otpLoginPanel.hidden;
      if (passwordLoginPanel) {
        passwordLoginPanel.hidden = !otpLoginPanel.hidden;
      }
      otpSwitch.textContent = otpLoginPanel.hidden ? "Use OTP to Login" : "Use Password to Login";
    });
  }

  configureOtpFlow({
    phoneSelector: "[data-login-phone]",
    otpSelector: "[data-login-otp]",
    sendSelector: "[data-send-login-otp]",
    verifySelector: "[data-verify-login-otp]",
    statusSelector: "[data-login-otp-status]",
    tokenSelector: "[data-login-otp-token]",
    verifiedPhoneSelector: "[data-login-otp-phone]",
    submitSelector: "[data-login-otp-submit]",
    initialMessage: "Verify your registered mobile number to login.",
    beforeSend: async (_mobileWithCountryCode, visiblePhone) => {
      const response = await fetch(`/login/otp/check-phone?phone=${encodeURIComponent(visiblePhone)}`);
      if (!response.ok) {
        return { ok: false, message: "Could not check this mobile number." };
      }
      const data = await response.json();
      return {
        ok: Boolean(data.registered),
        message: data.message || "This mobile number is not registered.",
      };
    },
  });
});
