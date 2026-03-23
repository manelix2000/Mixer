import { initBotId } from "botid/client/core";

initBotId({
  protect: [
    {
      path: "/api/contact",
      method: "POST"
    },
    {
      path: "/api/newsletter/subscribe",
      method: "POST"
    }
  ]
});
